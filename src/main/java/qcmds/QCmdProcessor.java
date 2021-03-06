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

import axoloti.MainFrame;
import axoloti.Patch;
import axoloti.SerialConnection;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.SwingUtilities;

/**
 *
 * @author Johannes Taelman
 */
public class QCmdProcessor implements Runnable {

    BlockingQueue<QCmd> queue;
    private BlockingQueue<QCmd> queueResponse;
    public SerialConnection serialconnection;
    private Patch patch;
    MainFrame mainframe;
    PeriodicPinger pinger;
    Thread pingerThread;
    PeriodicDialTransmitter dialTransmitter;
    Thread dialTransmitterThread;

    class PeriodicPinger implements Runnable {

        @Override
        public void run() {
            while (true) {
                try {
                    Thread.sleep(MainFrame.prefs.getPollInterval());
                } catch (InterruptedException ex) {
                    Logger.getLogger(QCmdProcessor.class.getName()).log(Level.SEVERE, null, ex);
                }
                if (queue.isEmpty() && serialconnection.isConnected()) {
                    queue.add(new QCmdPing());
                }
            }
        }
    }

    class PeriodicDialTransmitter implements Runnable {

        @Override
        public void run() {
            while (true) {
                if (queue.isEmpty() && serialconnection.isConnected()) {
                    queue.add(new QCmdGuiDialTx());
                }
                try {
                    Thread.sleep(5);
                } catch (InterruptedException ex) {
                    Logger.getLogger(QCmdProcessor.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        }
    }

    public QCmdProcessor() {
        queue = new ArrayBlockingQueue<QCmd>(10);
        queueResponse = new ArrayBlockingQueue<QCmd>(10);
        serialconnection = new SerialConnection(null, queueResponse);
        pinger = new PeriodicPinger();
        pingerThread = new Thread(pinger);
        dialTransmitter = new PeriodicDialTransmitter();
        dialTransmitterThread = new Thread(dialTransmitter);
    }

    public Patch getPatch() {
        return patch;
    }

    public boolean AppendToQueue(QCmd cmd) {
        return queue.add(cmd);
    }

    public void Panic() {
        queue.clear();
//        shellprocessor.Panic();
        serialconnection.Panic();
    }

    private void publish(final QCmd cmd) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                if (QCmdGUITask.class.isInstance(cmd)) {
                    ((QCmdGUITask) cmd).DoGUI(QCmdProcessor.this);
                }
                String m = ((QCmd) cmd).GetDoneMessage();
                if (m != null) {
                    MainFrame.mainframe.SetProgressMessage(m);
                }
            }
        });
    }
    private int progressValue = 0;

    private void setProgress(final int i) {
        if (i != progressValue) {
            progressValue = i;
            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    MainFrame.mainframe.SetProgressValue(i);
                }
            });
        }
    }

    private void publish(final String m) {
//        println(m);
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                MainFrame.mainframe.SetProgressMessage(m);
            }
        });
    }

    public void WaitQueueFinished() {
        while (true) {
            if (queue.isEmpty()) {
                break;
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException ex) {
                Logger.getLogger(QCmdProcessor.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }

    @Override
    public void run() {
        pingerThread.setName("PingerThread");
        pingerThread.start();
        dialTransmitterThread.setName("DialTransmitter");
        dialTransmitterThread.start();
        while (true) {
            setProgress(0);
            try {
                queueResponse.clear();
                QCmd cmd = queue.take();
                if (!((cmd instanceof QCmdPing) || (cmd instanceof QCmdGuiDialTx))) {
                    //System.out.println(cmd);
                    //setProgress((100 * (queue.size() + 1)) / (queue.size() + 2));
                }
                String m = cmd.GetStartMessage();
                if (m != null) {
                    publish(m);
                    println(m);
                }
                if (QCmdShellTask.class.isInstance(cmd)) {
                    //                shellprocessor.AppendToQueue((QCmdShellTask)cmd);
                    //                publish(queueResponse.take());
                    QCmd response = ((QCmdShellTask) cmd).Do(this);
                    if ((response != null)) {
                        ((QCmdGUITask) response).DoGUI(this);
                    }
                }
                if (QCmdSerialTask.class.isInstance(cmd)) {
                    if (serialconnection.isConnected()) {
                        serialconnection.AppendToQueue((QCmdSerialTask) cmd);
                        publish(queueResponse.take());
                    }
                }
                if (QCmdGUITask.class.isInstance(cmd)) {
                    publish(cmd);
                }
                m = cmd.GetDoneMessage();
                if (m != null) {
                    println(m);
                    publish(m);
                }
            } catch (InterruptedException ex) {
                Logger.getLogger(QCmdProcessor.class.getName()).log(Level.SEVERE, null, ex);
            }
            setProgress(0);
        }
    }

    public void println(final String s) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                Logger.getLogger(QCmdProcessor.class.getName()).log(Level.INFO, s);
            }
        });
    }

    public void SetPatch(Patch patch) {
        if (this.patch != null) {
            patch.Unlock();
        }
        this.patch = patch;
        serialconnection.setPatch(patch);
    }
}
