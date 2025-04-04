/*
 * Created on May 17, 2004
 *
 * Paros and its related class files.
 *
 * Paros is an HTTP/HTTPS proxy for assessing web application security.
 * Copyright (C) 2003-2004 Chinotec Technologies Company
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the Clarified Artistic License
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * Clarified Artistic License for more details.
 *
 * You should have received a copy of the Clarified Artistic License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */
// ZAP: 2013/01/16 Minor fix to prevent NPE
// ZAP: 2014/10/17 Issue 1308: Updated for latest icons
// ZAP: 2015/02/10 Issue 1528: Support user defined font size
// ZAP: 2015/09/07 Move icon loading to a utility class
// ZAP: 2019/06/01 Normalise line endings.
// ZAP: 2019/06/05 Normalise format/style.
// ZAP: 2020/11/05 Remove abstract modifier.
// ZAP: 2020/11/26 Use Log4j 2 classes for logging.
// ZAP: 2022/02/03 Removed deprecated loadIconImages()
// ZAP: 2022/08/05 Address warns with Java 18 (Issue 7389).
// ZAP: 2022/09/08 Use format specifiers instead of concatenation when logging.
// ZAP: 2023/01/10 Tidy up logger.
// ZAP: 2025/03/07 Save window size regardless of state.
package org.parosproxy.paros.view;

import java.awt.Dimension;
import java.awt.Frame;
import java.awt.Point;
import java.awt.Toolkit;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.WindowEvent;
import java.awt.event.WindowStateListener;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;
import javax.swing.JFrame;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.parosproxy.paros.Constant;
import org.zaproxy.zap.utils.DisplayUtils;

/**
 * Generic Frame, which handles some basic properties.
 *
 * <ul>
 *   <li>Sets the icon(s) for the frame, which are the ZAP icons
 *   <li>Centers the frame on screen
 *   <li>Sets the frame to _not_ visible
 *   <li>Sets a common font for the frame
 *   <li>Sets a default title (ZAP application name)
 *   <li>Preserves window state, location and size correctly (will survive multiple session)
 * </ul>
 *
 * Hint for implementors: If you use this class, don't use {@link #setSize(Dimension)}, but {@link
 * #setPreferredSize(Dimension)} instead. Also, don't use {@link #setLocation(Point)}. This abstract
 * class will automatically take care of size and position.
 */
@SuppressWarnings("serial")
public class AbstractFrame extends JFrame {

    private static final long serialVersionUID = 6751593232255236597L;

    private static final String PREF_WINDOW_STATE = "window.state";
    private static final String PREF_WINDOW_SIZE = "window.size";
    private static final String PREF_WINDOW_POSITION = "window.position";

    private static final int WINDOW_DEFAULT_WIDTH = 800;
    private static final int WINDOW_DEFAULT_HEIGHT = 600;

    /** Hint: Preferences are only saved by package. We have to use a prefix for separation. */
    private final Preferences preferences;

    private final String prefnzPrefix;
    private static final Logger LOGGER = LogManager.getLogger(AbstractFrame.class);

    /** This is the default constructor */
    public AbstractFrame() {
        this(null);
    }

    protected AbstractFrame(String prefnzPrefix) {
        super();
        this.preferences = Preferences.userNodeForPackage(getClass());
        this.prefnzPrefix =
                prefnzPrefix == null
                        ? this.getClass().getSimpleName() + "."
                        : StringUtils.left(prefnzPrefix, Preferences.MAX_KEY_LENGTH);
        initialize();
    }

    /** This method initializes this */
    private void initialize() {
        // ZAP: Rebrand
        this.setIconImages(DisplayUtils.getZapIconImages());

        this.setVisible(false);
        this.setTitle(Constant.PROGRAM_NAME);

        final Dimension dim = restoreWindowSize();
        if (dim == null) {
            this.setSize(WINDOW_DEFAULT_WIDTH, WINDOW_DEFAULT_HEIGHT);
        }
        final Point point = restoreWindowLocation();
        if (point == null) {
            centerFrame();
        }
        restoreWindowState();
        this.addWindowStateListener(new FrameWindowStateListener());
        this.addComponentListener(new FrameResizedListener());
    }

    /** Centre this frame. */
    public void centerFrame() {
        final Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        final Dimension frameSize = this.getSize();
        if (frameSize.height > screenSize.height) {
            frameSize.height = screenSize.height;
        }
        if (frameSize.width > screenSize.width) {
            frameSize.width = screenSize.width;
        }
        this.setLocation(
                (screenSize.width - frameSize.width) / 2,
                (screenSize.height - frameSize.height) / 2);
    }

    /**
     * @param windowstate integer value, see {@link JFrame#getExtendedState()}
     */
    private void saveWindowState(int windowstate) {
        if ((windowstate & Frame.ICONIFIED) == Frame.ICONIFIED) {
            preferences.put(
                    prefnzPrefix + PREF_WINDOW_STATE, SimpleWindowState.ICONIFIED.toString());
            LOGGER.debug("Saving preference {}={}", PREF_WINDOW_STATE, SimpleWindowState.ICONIFIED);
        }
        if ((windowstate & Frame.MAXIMIZED_BOTH) == Frame.MAXIMIZED_BOTH) {
            preferences.put(
                    prefnzPrefix + PREF_WINDOW_STATE, SimpleWindowState.MAXIMIZED.toString());
            LOGGER.debug("Saving preference {}={}", PREF_WINDOW_STATE, SimpleWindowState.MAXIMIZED);
        }
        if (windowstate == Frame.NORMAL) { // hint: Frame.NORMAL = 0, thats why no masking
            preferences.put(prefnzPrefix + PREF_WINDOW_STATE, SimpleWindowState.NORMAL.toString());
            LOGGER.debug("Saving preference {}={}", PREF_WINDOW_STATE, SimpleWindowState.NORMAL);
        }
    }

    /**
     * Loads and sets the last window state of the frame. Additionally, the last state will be
     * returned.
     *
     * @return last window state OR null
     */
    private SimpleWindowState restoreWindowState() {
        SimpleWindowState laststate = null;
        final String statestr = preferences.get(prefnzPrefix + PREF_WINDOW_STATE, null);
        LOGGER.debug("Restoring preference {}={}", PREF_WINDOW_STATE, statestr);
        if (statestr != null) {
            SimpleWindowState state = null;
            try {
                state = SimpleWindowState.valueOf(statestr);
            } catch (final IllegalArgumentException e) {
                state = null;
            }
            if (state != null) {
                switch (state) {
                    case ICONIFIED:
                        this.setExtendedState(Frame.ICONIFIED);
                        break;
                    case NORMAL:
                        this.setExtendedState(Frame.NORMAL);
                        break;
                    case MAXIMIZED:
                        this.setExtendedState(Frame.MAXIMIZED_BOTH);
                        break;
                    default:
                        LOGGER.error(
                                "Invalid window state (nothing will be changed): {}", statestr);
                }
            }
            laststate = state;
        }
        return laststate;
    }

    /**
     * Saves the size of this frame.
     *
     * @param size the dimensions being saved
     */
    private void saveWindowSize(Dimension size) {
        if (size != null) {
            LOGGER.debug("Saving preference {}={},{}", PREF_WINDOW_SIZE, size.width, size.height);
            this.preferences.put(prefnzPrefix + PREF_WINDOW_SIZE, size.width + "," + size.height);
        }
    }

    /**
     * Loads and set the saved size preferences for this frame.
     *
     * @return the size of the frame OR null, if there wasn't any preference.
     */
    private Dimension restoreWindowSize() {
        Dimension result = null;
        final String sizestr = preferences.get(prefnzPrefix + PREF_WINDOW_SIZE, null);
        if (sizestr != null) {
            int width = 0;
            int height = 0;
            final String[] sizes = sizestr.split("[,]");
            try {
                width = Integer.parseInt(sizes[0].trim());
                height = Integer.parseInt(sizes[1].trim());
            } catch (final Exception e) {
                // ignoring, cause is prevented by default values;
            }
            if (width > 0 && height > 0) {
                result = new Dimension(width, height);
                LOGGER.debug(
                        "Restoring preference {}={},{}",
                        PREF_WINDOW_SIZE,
                        result.width,
                        result.height);
                this.setSize(result);
            }
        }
        return result;
    }

    /**
     * Saves the location of this frame, but only, if window state is 'normal'. If window state is
     * iconified or maximized, the location is not saved!
     *
     * @param point
     */
    private void saveWindowLocation(Point point) {
        if (point != null) {
            if (getExtendedState() == Frame.NORMAL) {
                LOGGER.debug("Saving preference {}={},{}", PREF_WINDOW_POSITION, point.x, point.y);
                this.preferences.put(prefnzPrefix + PREF_WINDOW_POSITION, point.x + "," + point.y);
            } else {
                LOGGER.debug(
                        "Preference {} not saved, cause window state is not 'normal'.",
                        PREF_WINDOW_POSITION);
            }
        }
    }

    /**
     * Loads and set the saved position preferences for this frame.
     *
     * @return the size of the frame OR null, if there wasn't any preference.
     */
    private Point restoreWindowLocation() {
        Point result = null;
        final String sizestr = preferences.get(prefnzPrefix + PREF_WINDOW_POSITION, null);
        if (sizestr != null) {
            int x = 0;
            int y = 0;
            final String[] sizes = sizestr.split("[,]");
            try {
                x = Integer.parseInt(sizes[0].trim());
                y = Integer.parseInt(sizes[1].trim());
            } catch (final Exception e) {
                // ignoring, cause is prevented by default values;
            }
            if (x > 0 && y > 0) {
                result = new Point(x, y);
                LOGGER.debug(
                        "Restoring preference {}={},{}", PREF_WINDOW_POSITION, result.x, result.y);
                this.setLocation(result);
            }
        }
        return result;
    }

    @Override
    public void dispose() {
        super.dispose();
        try {
            this.preferences.flush();
        } catch (final BackingStoreException e) {
            LOGGER.error("Error while saving the preferences", e);
        }
    }

    /*
     * ========================================================================
     */

    private final class FrameWindowStateListener implements WindowStateListener {
        @Override
        public void windowStateChanged(WindowEvent e) {
            saveWindowState(e.getNewState());
        }
    }

    private final class FrameResizedListener extends ComponentAdapter {

        @Override
        public void componentResized(ComponentEvent e) {
            if (e.getComponent() != null) {
                saveWindowSize(e.getComponent().getSize());
            }
        }

        @Override
        public void componentMoved(ComponentEvent e) {
            if (e.getComponent() != null) {
                saveWindowLocation(e.getComponent().getLocation());
            }
        }
    }

    /** Simplified version for easier handling of the states ... */
    private enum SimpleWindowState {
        ICONIFIED,
        NORMAL,
        MAXIMIZED;
    }
} //  @jve:visual-info  decl-index=0 visual-constraint="31,17"
