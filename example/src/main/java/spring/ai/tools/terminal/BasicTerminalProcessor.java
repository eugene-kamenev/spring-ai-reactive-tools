package spring.ai.tools.terminal;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;

import org.fusesource.jansi.io.AnsiProcessor;
import org.slf4j.Logger;

/**
 * ANSI processor providing <code>process*</code> corresponding to ANSI escape codes.
 * This class methods implementations are empty: subclasses should actually perform the
 * ANSI escape behaviors by implementing active code in <code>process*</code> methods.
 */
public class BasicTerminalProcessor extends AnsiProcessor {

    private static final Logger log = org.slf4j.LoggerFactory.getLogger(BasicTerminalProcessor.class);

    private boolean changeWindowDetected = false;
    private boolean eraseScreenDetected = false;

   
    public BasicTerminalProcessor(OutputStream out) {
        super(out);
    }

    public boolean isChangeWindowDetected() {
        return changeWindowDetected;
    }

    public boolean isEraseScreenDetected() {
        return eraseScreenDetected;
    }

    /**
     * Process <code>CSI u</code> ANSI code, corresponding to
     * <code>RCP – Restore Cursor Position</code>
     *
     * @throws IOException IOException
     */
    @Override
    protected void processRestoreCursorPosition() throws IOException {
        log.debug("Restore cursor position");
    }

    /**
     * Process <code>CSI s</code> ANSI code, corresponding to
     * <code>SCP – Save Cursor Position</code>
     *
     * @throws IOException IOException
     */
    @Override
    protected void processSaveCursorPosition() throws IOException {
        log.debug("Save cursor position");
    }

    /**
     * Process <code>CSI L</code> ANSI code, corresponding to
     * <code>IL – Insert Line</code>
     *
     * @param optionInt option
     * @throws IOException IOException
     * @since 1.16
     */
    @Override
    protected void processInsertLine(int optionInt) throws IOException {
        log.debug("Insert line:" + optionInt);
    }

    /**
     * Process <code>CSI M</code> ANSI code, corresponding to
     * <code>DL – Delete Line</code>
     *
     * @param optionInt option
     * @throws IOException IOException
     * @since 1.16
     */
    @Override
    protected void processDeleteLine(int optionInt) throws IOException {
        log.debug("Delete line:" + optionInt);
    }

    /**
     * Process <code>CSI n T</code> ANSI code, corresponding to
     * <code>SD – Scroll Down</code>
     *
     * @param optionInt option
     * @throws IOException IOException
     */
    @Override
    protected void processScrollDown(int optionInt) throws IOException {
        log.debug("Scroll down: " + optionInt);
    }

    /**
     * Process <code>CSI n U</code> ANSI code, corresponding to
     * <code>SU – Scroll Up</code>
     *
     * @param optionInt option
     * @throws IOException IOException
     */
    @Override
    protected void processScrollUp(int optionInt) throws IOException {
        log.debug("Scroll up: " + optionInt);
    }

    /**
     * Process <code>CSI n J</code> ANSI code, corresponding to
     * <code>ED – Erase in Display</code>
     *
     * @param eraseOption eraseOption
     * @throws IOException IOException
     */
    @Override
    protected void processEraseScreen(int eraseOption) throws IOException {
        log.debug("Erase screen: " + eraseOption);
        this.eraseScreenDetected = true;
    }

    /**
     * Process <code>CSI n K</code> ANSI code, corresponding to
     * <code>ED – Erase in Line</code>
     *
     * @param eraseOption eraseOption
     * @throws IOException IOException
     */
    @Override
    protected void processEraseLine(int eraseOption) throws IOException {
        log.debug("Erase line:" + eraseOption);
    }

    /**
     * process <code>SGR</code> other than <code>0</code> (reset),
     * <code>30-39</code> (foreground),
     * <code>40-49</code> (background), <code>90-97</code> (foreground high
     * intensity) or
     * <code>100-107</code> (background high intensity)
     *
     * @param attribute attribute
     * @throws IOException IOException
     */
    @Override
    protected void processSetAttribute(int attribute) throws IOException {
        log.debug("Set attribute: " + attribute);
    }

    /**
     * process <code>SGR 30-37</code> or <code>SGR 90-97</code> corresponding to
     * <code>Set text color (foreground)</code> either in normal mode or high
     * intensity.
     *
     * @param color  the text color
     * @param bright is high intensity?
     * @throws IOException IOException
     */
    @Override
    protected void processSetForegroundColor(int color, boolean bright) throws IOException {
        log.debug("Set foreground color: " + color);
    }

    /**
     * process <code>SGR 38</code> corresponding to
     * <code>extended set text color (foreground)</code>
     * with a palette of 255 colors.
     *
     * @param paletteIndex the text color in the palette
     * @throws IOException IOException
     */
    @Override
    protected void processSetForegroundColorExt(int paletteIndex) throws IOException {
        log.debug("Set foreground color ext: " + paletteIndex);
    }

    /**
     * process <code>SGR 40-47</code> or <code>SGR 100-107</code> corresponding to
     * <code>Set background color</code> either in normal mode or high intensity.
     *
     * @param color  the background color
     * @param bright is high intensity?
     * @throws IOException IOException
     */
    @Override
    protected void processSetBackgroundColor(int color, boolean bright) throws IOException {
        log.debug("Set background color: " + color);
    }

    /**
     * process <code>SGR 48</code> corresponding to
     * <code>extended set background color</code>
     * with a palette of 255 colors.
     *
     * @param paletteIndex the background color in the palette
     * @throws IOException IOException
     */
    @Override
    protected void processSetBackgroundColorExt(int paletteIndex) throws IOException {
        log.debug("Set background color ext: " + paletteIndex);
    }

    /**
     * process <code>SGR 48</code> corresponding to
     * <code>extended set background color</code>
     * with a 24 bits RGB definition of the color.
     *
     * @param r red
     * @param g green
     * @param b blue
     * @throws IOException IOException
     */
    @Override
    protected void processSetBackgroundColorExt(int r, int g, int b) throws IOException {
        log.debug("Set background color ext: " + r + "," + g + "," + b);
    }

    /**
     * process <code>SGR 39</code> corresponding to
     * <code>Default text color (foreground)</code>
     *
     * @throws IOException IOException
     */
    @Override
    protected void processDefaultTextColor() throws IOException {
        log.debug("Default text color");
    }

    /**
     * process <code>SGR 49</code> corresponding to
     * <code>Default background color</code>
     *
     * @throws IOException IOException
     */
    @Override
    protected void processDefaultBackgroundColor() throws IOException {
        log.debug("Default background color");
    }

    /**
     * process <code>SGR 0</code> corresponding to <code>Reset / Normal</code>
     *
     * @throws IOException IOException
     */
    @Override
    protected void processAttributeReset() throws IOException {
        log.debug("Attribute Reset");
    }

    /**
     * process <code>CSI n ; m H</code> corresponding to
     * <code>CUP – Cursor Position</code> or
     * <code>CSI n ; m f</code> corresponding to
     * <code>HVP – Horizontal and Vertical Position</code>
     *
     * @param row row
     * @param col col
     * @throws IOException IOException
     */
    @Override
    protected void processCursorTo(int row, int col) throws IOException {
        log.debug("Cursor to: " + row + "," + col);
    }

    /**
     * process <code>CSI n G</code> corresponding to
     * <code>CHA – Cursor Horizontal Absolute</code>
     *
     * @param x the column
     * @throws IOException IOException
     */
    @Override
    protected void processCursorToColumn(int x) throws IOException {
        log.debug("Cursor to column: " + x);
    }

    /**
     * process <code>CSI n F</code> corresponding to
     * <code>CPL – Cursor Previous Line</code>
     *
     * @param count line count
     * @throws IOException IOException
     */
    @Override
    protected void processCursorUpLine(int count) throws IOException {
        log.debug("Cursor up line: " + count);
    }

    /**
     * process <code>CSI n E</code> corresponding to
     * <code>CNL – Cursor Next Line</code>
     *
     * @param count line count
     * @throws IOException IOException
     */
    @Override
    protected void processCursorDownLine(int count) throws IOException {
        log.debug("Cursor down line: " + count);
    }

    /**
     * process <code>CSI n D</code> corresponding to <code>CUB – Cursor Back</code>
     *
     * @param count count
     * @throws IOException IOException
     */
    @Override
    protected void processCursorLeft(int count) throws IOException {
        log.debug("Cursor left: " + count);
    }

    /**
     * process <code>CSI n C</code> corresponding to
     * <code>CUF – Cursor Forward</code>
     *
     * @param count count
     * @throws IOException IOException
     */
    @Override
    protected void processCursorRight(int count) throws IOException {
        log.debug("Cursor right: " + count);
    }

    /**
     * process <code>CSI n B</code> corresponding to <code>CUD – Cursor Down</code>
     *
     * @param count count
     * @throws IOException IOException
     */
    @Override
    protected void processCursorDown(int count) throws IOException {
        log.debug("Cursor down: " + count);
    }

    /**
     * process <code>CSI n A</code> corresponding to <code>CUU – Cursor Up</code>
     *
     * @param count count
     * @throws IOException IOException
     */
    @Override
    protected void processCursorUp(int count) throws IOException {
        log.debug("Cursor up: " + count);
    }

    /**
     * Process Unknown Extension
     *
     * @param options options
     * @param command command
     */
    @Override
    protected void processUnknownExtension(ArrayList<Object> options, int command) {
        log.debug("Unknown extension: " + command);
        for (Object option : options) {
            log.debug("Option: " + option);
        }
    }

    /**
     * process <code>OSC 0;text BEL</code> corresponding to
     * <code>Change Window and Icon label</code>
     *
     * @param label window title name
     */
    @Override
    protected void processChangeIconNameAndWindowTitle(String label) {
        processChangeIconName(label);
        processChangeWindowTitle(label);
    }

    /**
     * process <code>OSC 1;text BEL</code> corresponding to
     * <code>Change Icon label</code>
     *
     * @param label icon label
     */
    @Override
    protected void processChangeIconName(String label) {
        log.debug("Change icon name: " + label);
    }

    /**
     * process <code>OSC 2;text BEL</code> corresponding to
     * <code>Change Window title</code>
     *
     * @param label window title text
     */
    @Override
    protected void processChangeWindowTitle(String label) {
        log.debug("Change window title: " + label);
        this.changeWindowDetected = true;
    }

    /**
     * Process unknown <code>OSC</code> command.
     *
     * @param command command
     * @param param   param
     */
    @Override
    protected void processUnknownOperatingSystemCommand(int command, String param) {
        log.debug("Unknown OSC command: " + command);
        log.debug("Unknown OSC param: " + param);
    }

    @Override
    protected void processCharsetSelect(int set, char seq) {
        log.debug("Charset select: " + set);
        log.debug("Charset select seq: " + seq);
    }

}
