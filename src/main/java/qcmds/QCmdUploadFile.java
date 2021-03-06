/**
 * Copyright (C) 2013, 2014 Johannes Taelman
 *
 * This file is part of Axoloti.
 *
 * Axoloti is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 *
 * Axoloti is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * Axoloti. If not, see <http://www.gnu.org/licenses/>.
 */
package qcmds;

import axoloti.SerialConnection;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import jssc.SerialPortException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Johannes Taelman
 */
public class QCmdUploadFile implements QCmdSerialTask {

    File f;
    String filename;

    public QCmdUploadFile(File f, String filename) {
        this.f = f;
        this.filename = filename;
    }

    @Override
    public String GetStartMessage() {
        try {
            return "Start uploading file " + f.getCanonicalPath() + " to sdcard : " + filename;
        } catch (IOException ex) {
            Logger.getLogger(QCmdUploadFile.class.getName()).log(Level.SEVERE, null, ex);
            return "";
        }
    }

    @Override
    public String GetDoneMessage() {
        return "Done uploading file";
    }

    @Override
    public QCmd Do(SerialConnection serialConnection) {
        serialConnection.ClearSync();
        try {
            Logger.getLogger(QCmdUploadFile.class.getName()).log(Level.INFO, "uploading: " + f.getAbsolutePath());
            int tlength = (int) f.length();
            serialConnection.TransmitCreateFile(filename, tlength);
            FileInputStream inputStream = new FileInputStream(f);
            int MaxBlockSize = 32768;
            int remLength = tlength;
            int pct = 0;
            do {
                int l;
                if (remLength > MaxBlockSize) {
                    l = MaxBlockSize;
                    remLength -= MaxBlockSize;
                } else {
                    l = remLength;
                    remLength = 0;
                }
                byte[] buffer = new byte[l];
                int nRead = inputStream.read(buffer, 0, l);
                if (nRead != l) {
                    Logger.getLogger(QCmdUploadFile.class.getName()).log(Level.SEVERE, "file size wrong?" + nRead);
                }
                serialConnection.TransmitAppendFile(buffer);
                int newpct = (100 * (tlength - remLength) / tlength);
                if (newpct != pct) {
                    Logger.getLogger(QCmdUploadFile.class.getName()).log(Level.INFO, "uploading : " + newpct + "%");
                }
                pct = newpct;
            } while (remLength > 0);

            inputStream.close();
            serialConnection.TransmitCloseFile();
            return this;
        } catch (FileNotFoundException ex) {
            Logger.getLogger(QCmdUploadFile.class.getName()).log(Level.SEVERE, "FileNotFoundException", ex);
        } catch (IOException ex) {
            Logger.getLogger(QCmdUploadFile.class.getName()).log(Level.SEVERE, "IOException", ex);
        } catch (SerialPortException ex) {
            Logger.getLogger(QCmdUploadFile.class.getName()).log(Level.SEVERE, "SerialPortException", ex);
        }
        return new QCmdDisconnect();
    }

}
