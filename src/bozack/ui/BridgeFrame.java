
package bozack.ui;

import javax.swing.JFrame;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JProgressBar;
import javax.swing.JMenuItem;
import javax.swing.JMenuBar;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JOptionPane;
import javax.swing.ButtonGroup;
import javax.swing.event.MenuListener;
import javax.swing.event.MenuEvent;
import java.util.ArrayList;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.MidiDevice;
import javax.sound.midi.MidiDevice.Info;
import javax.sound.midi.Sequencer;
import javax.sound.midi.Synthesizer;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MidiUnavailableException;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;

import bozack.Note;
import bozack.NoteSet;
import bozack.NoteHashMap;
import bozack.midi.Bridge;
import bozack.midi.PianoKeyEmulator;
import bozack.midi.receiver.CustomReceiver;
import bozack.midi.receiver.DumpRelay;
import bozack.midi.receiver.Stabilizer;
import bozack.midi.event.FramePainter;
import bozack.ui.KeyPanel;
import bozack.ui.ConnectionPanel;
import bozack.ui.AboutDialog;
import bozack.ui.LayoutDialog;

public final class BridgeFrame extends JFrame {

    private static final int POS_X  = 60;
    private static final int POS_Y  = 10;
    private static final int WIDTH  = 1200;
    private static final int HEIGHT = 600;
    private static NoteSet noteSet;
    private static int IDX_COMP_CONPANEL = 0;
    private static int IDX_COMP_KEYPANEL = 1;

    private Bridge bridge;
    private MidiDevice deviceIn;
    private Synthesizer deviceOut;
    private CustomReceiver receiver;
    private PianoKeyEmulator pianoKey;

    public BridgeFrame() {
        this.setBounds(POS_X, POS_Y, WIDTH, HEIGHT);

        CustomReceiver recv = new DumpRelay();
        this.receiver = recv;
        recv.addNoteEventListener(new FramePainter(this));

        this.bridge = new Bridge();
        ArrayList devices = Bridge.getDevices();
        this.deviceIn  = (MidiDevice)(devices.get(0));
        try {
            this.deviceOut = MidiSystem.getSynthesizer();
            this.connectDevice();
        } catch (MidiUnavailableException e) {
            System.err.println(e.getMessage());
            System.exit(0);
        }
        this.appendMenu();

        this.paintConnectionPanel();
        this.noteSet = new NoteSet();
        this.paintKeyPanel(this.noteSet);

        // register Software Keyboard emulation
        this.pianoKey = new PianoKeyEmulator(recv);
        this.addKeyListener(this.pianoKey);

        this.setVisible(true);
    }

    public void setDeviceIn(MidiDevice device) {
        this.deviceIn.close();
        this.deviceIn = device;
    }

    public void setDeviceOut(Synthesizer device) {
        this.deviceOut.close();
        this.deviceOut = device;
    }

    public void setReciever(CustomReceiver recv) {
        this.deviceIn.close();
        this.deviceOut.close();

        recv.addNoteEventListener(new FramePainter(this));

        // replace key emulator instance
        this.removeKeyListener(this.pianoKey);
        this.pianoKey = new PianoKeyEmulator(recv);
        this.addKeyListener(this.pianoKey);

        this.receiver = recv;
    }

    private void connectDevice()
        throws MidiUnavailableException {

        this.bridge.connect(this.deviceIn, this.deviceOut
            , this.receiver);
        this.paintConnectionPanel();

        System.out.println("Device reconnected");
        System.out.println(" - IN: " 
            + this.deviceIn.getDeviceInfo().getName());
        System.out.println(" - OUT: " 
            + this.deviceOut.getDeviceInfo().getName());
        System.out.println(" - RECV: " 
            + this.receiver.getClass().getName());
    }

    private void midiBeep(int note) {
        try {
            ShortMessage sm_on = new ShortMessage();
            sm_on.setMessage(ShortMessage.NOTE_ON, note, 100);
            ShortMessage sm_off = new ShortMessage();
            sm_off.setMessage(ShortMessage.NOTE_OFF, note, 100);

            this.receiver.handleShortMessage(sm_on, 0);
            this.receiver.handleShortMessage(sm_off, 0);
        } catch (InvalidMidiDataException ime) {
            System.out.println("beep failed" + ime.getMessage());
        }
    }

    private void appendMenu() {
        JMenu menuProp = new JMenu("Filter mode");
        ButtonGroup groupProp  = new ButtonGroup();
        JMenuItem menuPropSimple = new JRadioButtonMenuItem(
            "DumpRelay", true);
        menuPropSimple.addMouseListener(new ReconnectReceiverAdapter(
            this, new DumpRelay()));
        JMenuItem menuPropDes = new JRadioButtonMenuItem(
            "Stabilizer");
        menuPropDes.addMouseListener(new ReconnectReceiverAdapter(
            this, new Stabilizer()));
        groupProp.add(menuPropSimple);
        groupProp.add(menuPropDes);
        menuProp.add(menuPropSimple);
        menuProp.add(menuPropDes);

        JMenu menuDevice = new JMenu("MIDI Devices");
        JMenu menuDeviceController = new JMenu("Select Controller");
        JMenu menuDeviceSynth      = new JMenu("Select Synthesizer");

        // scan controller and synthesizer
        ButtonGroup groupController  = new ButtonGroup();
        ButtonGroup groupSynthesizer = new ButtonGroup();
        MidiDevice.Info infoDeviceIn  = this.deviceIn.getDeviceInfo();
        MidiDevice.Info infoDeviceOut = this.deviceOut.getDeviceInfo();
        MidiDevice.Info[] info = MidiSystem.getMidiDeviceInfo();
        ArrayList<MidiDevice> synth = new ArrayList<MidiDevice>();
        for (int i = 0; i < info.length; i++) {
            MidiDevice device;
            try {
                device = MidiSystem.getMidiDevice(info[i]);
            } catch (MidiUnavailableException e) {
                continue;
            }
            boolean checked = false;
            if (info[i].toString().equals(infoDeviceIn.toString())
                || info[i].toString().equals(infoDeviceOut.toString())) {
                System.out.println("match!-" + info[i].getName());
                checked = true;
            }
            JMenuItem menuItem = new JRadioButtonMenuItem(
                info[i].getName() + " / " + info[i].getVendor()
                , checked);

            if (device instanceof Synthesizer) {
                menuItem.addMouseListener(
                    new ReconnectDeviceOutAdapter(this, device));
                groupSynthesizer.add(menuItem);
                menuDeviceSynth.add(menuItem);
            }
            else {
                menuItem.addMouseListener(
                    new ReconnectDeviceInAdapter(this, device));
                groupController.add(menuItem);
                menuDeviceController.add(menuItem);
            }
        }

        menuDevice.add(menuDeviceController);
        menuDevice.add(menuDeviceSynth);
        menuDevice.add("Select Foot Device");

        JMenu menuHelp = new JMenu("Help");
        JMenuItem menuHelpAbout = new JMenuItem("About bozack");
        menuHelpAbout.addMouseListener(new HelpAdapter(this));
        JMenuItem menuHelpKey   = new JMenuItem("Keyboard layout");
        menuHelpKey.addMouseListener(new KeyLayoutAdapter(this));
        menuHelp.add(menuHelpAbout);
        menuHelp.add(menuHelpKey);

        JMenuBar menuBar = new JMenuBar();
        menuBar.add(menuProp);
        menuBar.add(menuDevice);
        menuBar.add(menuHelp);
        this.setJMenuBar(menuBar);
    }

    // Mouse Event Listeners
    protected class MenuAdapter extends MouseAdapter {
        protected BridgeFrame frame;
        public MenuAdapter(BridgeFrame f) { this.frame = f; }
    }
    private class HelpAdapter extends MenuAdapter {
        public HelpAdapter(BridgeFrame f) { super(f); }
        public void mouseReleased(MouseEvent e) {
            AboutDialog about = new AboutDialog(this.frame);
            about.setVisible(true);
        }
    }
    private class KeyLayoutAdapter extends MenuAdapter {
        public KeyLayoutAdapter(BridgeFrame f) { super(f); }
        public void mouseReleased(MouseEvent e) {
            LayoutDialog layout = new LayoutDialog(this.frame);
            layout.setVisible(true);
        }
    }
    protected class ReconnectDeviceAdapter extends MouseAdapter {
        protected BridgeFrame frame;
        protected MidiDevice device;
        public ReconnectDeviceAdapter(BridgeFrame f, MidiDevice d) {
            this.frame  = f;
            this.device = d;
        }
    }
    private class ReconnectDeviceInAdapter
        extends ReconnectDeviceAdapter {
        ReconnectDeviceInAdapter(BridgeFrame f, MidiDevice d) {
            super(f, d);
        }
        public void mouseReleased(MouseEvent e) {
            System.out.println("ReconnectDeviceInAdapter called");
            try {
                this.frame.midiBeep(90);
                this.frame.setDeviceIn(this.device);
                this.frame.connectDevice();
                this.frame.midiBeep(95);
            } catch (MidiUnavailableException mue) {
                JOptionPane.showMessageDialog(this.frame
                   , "Device not available: " + mue.getMessage()
                   , "Device Error"
                   , JOptionPane.WARNING_MESSAGE);
            }
        }
    }
    private class ReconnectDeviceOutAdapter
        extends ReconnectDeviceAdapter {
        ReconnectDeviceOutAdapter(BridgeFrame f, MidiDevice d) {
            super(f, d);
        }
        public void mouseReleased(MouseEvent e) {
            if (false == this.device instanceof Synthesizer) {
                JOptionPane.showMessageDialog(this.frame
                   , "This device is not a Synthesizer"
                   , "Device Error"
                   , JOptionPane.WARNING_MESSAGE);
                return;
            }
            try {
                this.frame.midiBeep(90);
                this.frame.setDeviceOut((Synthesizer)this.device);
                this.frame.connectDevice();
                this.frame.midiBeep(95);
            } catch (MidiUnavailableException mue) {
                JOptionPane.showMessageDialog(this.frame
                   , "Device not available: " + mue.getMessage()
                   , "Device Error"
                   , JOptionPane.WARNING_MESSAGE);
            }
        }
    }
    protected class ReconnectReceiverAdapter extends MouseAdapter {
        private BridgeFrame frame;
        private CustomReceiver receiver;
        public ReconnectReceiverAdapter(BridgeFrame f, CustomReceiver r) {
            this.frame = f;
            this.receiver = r;
        }
        public void mouseReleased(MouseEvent e) {
            try {
                this.frame.setReciever(this.receiver);
                this.frame.connectDevice();
                this.frame.midiBeep(95);
            } catch (MidiUnavailableException mue) {
                JOptionPane.showMessageDialog(this.frame
                   , "This receiver doesn't match selected devices: "
                       + mue.getMessage()
                   , "Receiver Connect Error"
                   , JOptionPane.WARNING_MESSAGE);
            }

        }
    }

    public void paintConnectionPanel() {
        Container contentPane = this.getContentPane();
        ConnectionPanel cp = new ConnectionPanel(
            this.deviceIn, deviceOut
            , this.receiver.getClass().getSimpleName());
        contentPane.add(cp, IDX_COMP_CONPANEL);
        contentPane.validate();
    }

    public void paintKeyPanel (NoteSet onNote) {
        this.paintKeyPanel(onNote, new NoteSet(), new NoteHashMap());
    }

    public void paintKeyPanel (
        NoteSet onNote,
        NoteSet assistedNote,
        NoteHashMap pickupRelation)
    {
        Container contentPane = this.getContentPane();
        this.noteSet = onNote;
        KeyPanel kp = new KeyPanel(this.noteSet
            , assistedNote, pickupRelation);
        try {
            contentPane.remove(IDX_COMP_KEYPANEL);
        } catch (Exception e) { }
        contentPane.add(kp, IDX_COMP_KEYPANEL);
        contentPane.validate();
    }
}

