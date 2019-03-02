/* -*- mode: java; c-basic-offset: 2; indent-tabs-mode: nil -*- */


/*
   .java, a plugin for the ArduinoIDE Tool menu.
    Create a .uf2 file from a .bin file located in the sketch folder.
    The IDE menu command 'Sketch/Export compiled Binary' should be used 
    to create the .bin file.
   
   ------------------
   
   This work is based on:
   
       ESP8266FS.java,
       an Arduino plugin to put the contents of the sketch's "data" subfolder
       into an SPIFFS partition image and upload it to an ESP8266 MCU
       from the esp8266/arduino-esp8266fs-plugin repository 
       at 'https://github.com/esp8266/arduino-esp8266fs-plugin'
       Copyright (c) 2015 Hristo Gochkov (ficeto at ficeto dot com)
 
   and on:
   
       bin2uf2.js,
       from the adafruit/uf2-samdx1 branch of the Microsoft/uf2-samdx1 repository 
       at 'https://github.com/adafruit/uf2-samdx1'
   
   ------------------
   
   
   This program is free software; you can redistribute it and/or modify
   it under the terms of the GNU General Public License as published by
   the Free Software Foundation; either version 2 of the License, or
   (at your option) any later version.
 
   This program is distributed in the hope that it will be useful,
   but WITHOUT ANY WARRANTY; without even the implied warranty of
   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
   GNU General Public License for more details.
 
   You should have received a copy of the GNU General Public License
   along with this program; if not, write to the Free Software Foundation,
   Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package com.makeUF2.makeUF2;

import java.io.File;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;

import java.text.SimpleDateFormat;
import java.util.*;
// import java.util.Date;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import javax.swing.JOptionPane;

import processing.app.PreferencesData;
import processing.app.Editor;
import processing.app.Base;
import processing.app.BaseNoGui;
import processing.app.Platform;
import processing.app.Sketch;
// import processing.app.SketchData;
import processing.app.tools.Tool;
import processing.app.helpers.ProcessUtils;
import processing.app.debug.TargetPlatform;

import org.apache.commons.codec.digest.DigestUtils;
import processing.app.helpers.FileUtils;

import cc.arduino.files.DeleteFilesOnShutdown;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import org.apache.commons.codec.binary.Hex;
import java.util.Arrays;

/**
 * Example Tools menu entry.
 */
public class makeUF2 implements Tool {
    Editor editor;


    public void init(Editor editor) {
        this.editor = editor;
    }


    public String getMenuTitle() {
        return "makeUF2 - UF2 file creator";
    }

    private String getBuildFolderPath(Sketch s) {
        // first of all try the getBuildPath() function introduced with IDE 1.6.12
        // see commit arduino/Arduino#fd1541eb47d589f9b9ea7e558018a8cf49bb6d03
        try {
            String buildpath = s.getBuildPath().getAbsolutePath();
            return buildpath;
        } catch (IOException er) {
            editor.statusError(er);
        } catch (Exception er) {
            try {
                File buildFolder = FileUtils.createTempFolder("build", DigestUtils.md5Hex(s.getMainFilePath()) + ".tmp");
                return buildFolder.getAbsolutePath();
            } catch (IOException e) {
                editor.statusError(e);
            } catch (Exception e) {
                // Arduino 1.6.5 doesn't have FileUtils.createTempFolder
                // String buildPath = BaseNoGui.getBuildFolder().getAbsolutePath();
                java.lang.reflect.Method method;
                try {
                    method = BaseNoGui.class.getMethod("getBuildFolder");
                    File f = (File) method.invoke(null);
                    return f.getAbsolutePath();
                } catch (SecurityException ex) {
                    editor.statusError(ex);
                } catch (IllegalAccessException ex) {
                    editor.statusError(ex);
                } catch (InvocationTargetException ex) {
                    editor.statusError(ex);
                } catch (NoSuchMethodException ex) {
                    editor.statusError(ex);
                }
            }
        }
        return "";
    }

    private long getIntPref(String name) {
        String data = BaseNoGui.getBoardPreferences().get(name);
        if (data == null || data.contentEquals("")) return 0;
        if (data.startsWith("0x")) return Long.parseLong(data.substring(2), 16);
        else return Integer.parseInt(data);
    }

    private void writeUInt32LE(byte[] bbuf, long value, int position) {
        bbuf[position] = (byte)(value & 0xff);
        bbuf[position + 1] = (byte)((value >> 8) & 0xff);
        bbuf[position + 2] = (byte)((value >> 16) & 0xff);
        bbuf[position + 3] = (byte)((value >> 24) & 0xff);
        return;
    }

    private void createUF2() {

        /*  this probably isn't a valid way to determine whether a UF2 file is valid for this board...
            if(!BaseNoGui.getBoardPreferences().get("upload.tool").contains("bossac")){
              editor.statusError("makeUF2 is not Supported on "+BaseNoGui.getBoardPreferences().get("name"));
              return;
            } else {
              System.out.print("makeUF2 is supported on "+PreferencesData.get("target_platform")+"/"+PreferencesData.get("board"));
              System.out.println("makeUF2 is supported on "+BaseNoGui.getBoardPreferences().get("name"));
            }
        */

        String classPath = makeUF2.class.getProtectionDomain().getCodeSource().getLocation().getPath();
        //     System.out.println("\nmakeUF2 is running from "+classPath );

        String buildVariant = BaseNoGui.getBoardPreferences().get("build.variant");
        //     System.out.println("buildVariant is "+buildVariant );
        //load a list of all the .bin files and capture the last one found
        String binFile = "unknown";
        String binName = "unknown";
        int fileCount = 0;
        int defaultChoice = 1;
        File sketchFolder = editor.getSketch().getFolder();
        System.err.println();
        System.out.println("looking for bin file in sketch folder: " + sketchFolder);
        List < String > binList = new ArrayList < String > ();
        if (sketchFolder.exists() && sketchFolder.isDirectory()) {
            File[] files = sketchFolder.listFiles();
            if (files.length > 0) {
                for (File file: files) {
                    if (file.isFile() && file.getName().endsWith(".bin")) {
                        System.out.println("found " + file.getName());
                        binFile = file.getName();
                        binName = binFile.substring(0, binFile.lastIndexOf("."));
                        binList.add(binFile);
                        if (binFile.contains(buildVariant)) {
                            defaultChoice = fileCount;
                        }
                        fileCount++;
                    }
                }
            }
            if (fileCount == 1) {
                // binFile and binName are already set
            }
            if (fileCount > 1) {
                String[] pickList = binList.toArray(new String[0]);
                String binChoice = (String) JOptionPane.showInputDialog(null, "Pick the .bin file to convert...", "More than one .bin file found...", JOptionPane.QUESTION_MESSAGE, null, pickList, pickList[defaultChoice]);
                if (binChoice == null) {
                    String message99 = "More than one '.bin' file has been found in your sketch folder!\n    -- None selected, operation cancelled...";
                    JOptionPane.showMessageDialog(editor, message99, " Create", JOptionPane.INFORMATION_MESSAGE);
                    System.err.println();
                    editor.statusError(message99);
                    editor.statusError("makeUF2: operation canceled, too many '.bin' files...");
                    return;
                } else {
                    binFile = binChoice;
                    binName = binFile.substring(0, binFile.lastIndexOf("."));
                    System.out.println("binFile is " + binFile);
                    System.out.println("binName is " + binName);
                }
            }
            if (fileCount == 0) {
                String message0 = "No '.bin' file has been found in your sketch folder!\n    -- Use 'Sketch/Export compiled Binary' to create one...";
                JOptionPane.showMessageDialog(editor, message0, " Create", JOptionPane.INFORMATION_MESSAGE);
                System.err.println();
                editor.statusError(message0);
                editor.statusError("makeUF2: operation canceled, no '.bin' file found...");
                return;
            }
        }

        String dataPath = sketchFolder.getAbsolutePath();
        String sketchName = editor.getSketch().getName();
        String binPath = sketchFolder + "/" + binFile;
        String uf2Name = binName + ".uf2";
        String uf2FilePath = sketchFolder + "/" + binName + ".uf2";

        System.out.println("\nmakeUF2 - Creating UF2 file..." + uf2FilePath + "\n");

        try {
            // create the uf2 file 
            FileOutputStream uf2Stream = new FileOutputStream(uf2FilePath, false); // overwrite if existing
            FileInputStream binStream = new FileInputStream(binPath);

            long APP_START_ADDRESS = Long.decode(BaseNoGui.getBoardPreferences().get("upload.offset")); // write file above boot loader

            long UF2_MAGIC_START0 = 0x0A324655; // "UF2\n"
            long UF2_MAGIC_START1 = 0x9E5D5157; // Randomly selected
            long UF2_MAGIC_END = 0x0AB16F30; // Ditto

            long numBlocks = (binStream.getChannel().size() + 255) >>> 8; // number of 256-byte blocks in binPath input file;

            // build UF2 blocks from input file
            long endOfBinStream = binStream.getChannel().size();
            byte[] theChunk = new byte[256];
            int len;
            int pos = 0;
            while ((len = binStream.read(theChunk)) != -1) {

                // first build a buffer to hold the block
                byte[] block = new byte[512]; // 
                // zero the block
                Arrays.fill(block, (byte) 0); // data block is padded with zeros

                // now insert the 32-byte header
                writeUInt32LE(block, UF2_MAGIC_START0, 0); // First magic number, 0x0A324655 ("UF2\n")
                writeUInt32LE(block, UF2_MAGIC_START1, 4); // Second magic number, 0x9E5D5157
                writeUInt32LE(block, 0, 8); // flags
                writeUInt32LE(block, APP_START_ADDRESS + pos, 12); // Address in flash where the data should be written
                //             System.out.println("APP_START_ADDRESS + pos is: "+ Long.toHexString(APP_START_ADDRESS + pos)+" next pos is: "+ Integer.toHexString(pos));
                writeUInt32LE(block, 256, 16); // Number of bytes used in data (we write 256)
                writeUInt32LE(block, ((int)(pos / 256)), 20); // 
                //             System.out.println("block number is: "+(int)(pos/256)); // Sequential block number; starts at 0
                pos += 256;
                writeUInt32LE(block, numBlocks, 24); // Total number of blocks in file
                writeUInt32LE(block, 0, 28); // File size or board family ID or zero
                // now insert the 256-byte chunk of the input file 
                for (int i = 0; i < 256; ++i) {
                    block[i + 32] = theChunk[i];
                }
                // and insert the final magic number per spec
                writeUInt32LE(block, UF2_MAGIC_END, 512 - 4);

                //finally append the block to the uf2File
                uf2Stream.write(block);

                //             System.out.println(Hex.encodeHexString(block)); // print the block for debugging

            }
            binStream.close();
            uf2Stream.close();
        } catch (Exception e) {
            editor.statusError(e);
            editor.statusError(" Create Failed!");
        }

        editor.statusNotice("makeUF2 built Image... " + uf2Name);
        System.out.println("makeUF2 has built " + uf2Name + "\n in the sketch folder " + dataPath + ".\n  Copy that file onto your xxxxBOOT disk to install it");

    }

    public void run() {
        createUF2();
    }
}