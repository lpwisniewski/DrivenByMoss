// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2017-2020
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.controller.launchpad.controller;

import de.mossgrabers.controller.launchpad.LaunchpadConfiguration;
import de.mossgrabers.controller.launchpad.definition.ILaunchpadControllerDefinition;
import de.mossgrabers.framework.controller.AbstractControlSurface;
import de.mossgrabers.framework.controller.ButtonID;
import de.mossgrabers.framework.controller.color.ColorManager;
import de.mossgrabers.framework.controller.grid.IVirtualFader;
import de.mossgrabers.framework.controller.hardware.IHwButton;
import de.mossgrabers.framework.daw.IHost;
import de.mossgrabers.framework.daw.midi.DeviceInquiry;
import de.mossgrabers.framework.daw.midi.IMidiInput;
import de.mossgrabers.framework.daw.midi.IMidiOutput;
import de.mossgrabers.framework.utils.StringUtils;

import java.util.Map.Entry;


/**
 * The Launchpad control surface.
 *
 * @author J&uuml;rgen Mo&szlig;graber
 */
@SuppressWarnings("javadoc")
public class LaunchpadControlSurface extends AbstractControlSurface<LaunchpadConfiguration>
{
    public static final int                      LAUNCHPAD_BUTTON_SCENE1    = 89;                   // 1/4
    public static final int                      LAUNCHPAD_BUTTON_SCENE2    = 79;
    public static final int                      LAUNCHPAD_BUTTON_SCENE3    = 69;
    public static final int                      LAUNCHPAD_BUTTON_SCENE4    = 59;
    public static final int                      LAUNCHPAD_BUTTON_SCENE5    = 49;                   // ...
    public static final int                      LAUNCHPAD_BUTTON_SCENE6    = 39;
    public static final int                      LAUNCHPAD_BUTTON_SCENE7    = 29;
    public static final int                      LAUNCHPAD_BUTTON_SCENE8    = 19;                   // 1/32T

    public static final int                      LAUNCHPAD_FADER_1          = 21;
    public static final int                      LAUNCHPAD_FADER_2          = 22;
    public static final int                      LAUNCHPAD_FADER_3          = 23;
    public static final int                      LAUNCHPAD_FADER_4          = 24;
    public static final int                      LAUNCHPAD_FADER_5          = 25;
    public static final int                      LAUNCHPAD_FADER_6          = 26;
    public static final int                      LAUNCHPAD_FADER_7          = 27;
    public static final int                      LAUNCHPAD_FADER_8          = 28;

    public static final int                      LAUNCHPAD_LOGO             = 99;

    public static final int                      LAUNCHPAD_BUTTON_STATE_OFF = 0;
    public static final int                      LAUNCHPAD_BUTTON_STATE_ON  = 1;
    public static final int                      LAUNCHPAD_BUTTON_STATE_HI  = 4;

    public static final int                      CONTROL_MODE_OFF           = 0;
    public static final int                      CONTROL_MODE_REC_ARM       = 1;
    public static final int                      CONTROL_MODE_TRACK_SELECT  = 2;
    public static final int                      CONTROL_MODE_MUTE          = 3;
    public static final int                      CONTROL_MODE_SOLO          = 4;
    public static final int                      CONTROL_MODE_STOP_CLIP     = 5;

    private final ILaunchpadControllerDefinition definition;

    private final IVirtualFader []               virtualFaders              = new IVirtualFader [8];


    /**
     * Constructor.
     *
     * @param host The host
     * @param colorManager The color manager
     * @param configuration The configuration
     * @param output The midi output
     * @param input The midi input
     * @param definition The Launchpad definition
     */
    public LaunchpadControlSurface (final IHost host, final ColorManager colorManager, final LaunchpadConfiguration configuration, final IMidiOutput output, final IMidiInput input, final ILaunchpadControllerDefinition definition)
    {
        super (host, configuration, colorManager, output, input, new LaunchpadPadGrid (colorManager, output, definition), 800, 800);

        this.definition = definition;

        for (int i = 0; i < this.virtualFaders.length; i++)
            this.virtualFaders[i] = this.definition.createVirtualFader (this.pads, i);

        this.input.setSysexCallback (this::handleSysEx);
        this.output.sendSysex (DeviceInquiry.createQuery ());
    }


    /**
     * Is the user button pressed (mixer on MkII)?
     *
     * @return True if pressed
     */
    public boolean isUserPressed ()
    {
        return this.isPressed (ButtonID.USER);
    }


    /**
     * Set the launchpad to standalone mode.
     */
    public void setLaunchpadToStandalone ()
    {
        this.sendLaunchpadSysEx (this.definition.getStandaloneModeCommand ());
    }


    /**
     * Set the launchpad to program mode. All pads can freely controlled.
     */
    public void setLaunchpadToPrgMode ()
    {
        this.setLaunchpadMode (this.definition.getProgramModeCommand ());
    }


    private void setLaunchpadMode (final String data)
    {
        this.sendLaunchpadSysEx (data);

        for (final Entry<ButtonID, IHwButton> entry: this.getButtons ().entrySet ())
        {
            final ButtonID key = entry.getKey ();
            final int keyValue = key.ordinal ();
            if (ButtonID.PAD1.ordinal () < keyValue || ButtonID.PAD64.ordinal () > keyValue)
                entry.getValue ().getLight ().clearCache ();
        }
    }


    /**
     * Set the color of a fader (8 vertical pads).
     *
     * @param index The number of the fader (0-7)
     * @param color The color to set
     * @param isPan True for panorama layout
     */
    public void setupFader (final int index, final int color, final boolean isPan)
    {
        this.virtualFaders[index].setup (color, isPan);
    }


    /**
     * Set the faders value.
     *
     * @param index The index of the fader (0-7)
     * @param value The value to set
     */
    public void setFaderValue (final int index, final int value)
    {
        this.virtualFaders[index].setValue (value);
    }


    /**
     * Clear the faders cache.
     */
    public void clearFaders ()
    {
        for (int i = 0; i < 8; i++)
            this.setupFader (i, -1, false);
    }


    /** {@inheritDoc} */
    @Override
    protected void internalShutdown ()
    {
        this.definition.setLogoColor (this, LaunchpadColorManager.LAUNCHPAD_COLOR_BLACK);

        this.setTrigger (LaunchpadControlSurface.LAUNCHPAD_BUTTON_SCENE1, LaunchpadColorManager.LAUNCHPAD_COLOR_BLACK);
        this.setTrigger (LaunchpadControlSurface.LAUNCHPAD_BUTTON_SCENE2, LaunchpadColorManager.LAUNCHPAD_COLOR_BLACK);
        this.setTrigger (LaunchpadControlSurface.LAUNCHPAD_BUTTON_SCENE3, LaunchpadColorManager.LAUNCHPAD_COLOR_BLACK);
        this.setTrigger (LaunchpadControlSurface.LAUNCHPAD_BUTTON_SCENE4, LaunchpadColorManager.LAUNCHPAD_COLOR_BLACK);
        this.setTrigger (LaunchpadControlSurface.LAUNCHPAD_BUTTON_SCENE5, LaunchpadColorManager.LAUNCHPAD_COLOR_BLACK);
        this.setTrigger (LaunchpadControlSurface.LAUNCHPAD_BUTTON_SCENE6, LaunchpadColorManager.LAUNCHPAD_COLOR_BLACK);
        this.setTrigger (LaunchpadControlSurface.LAUNCHPAD_BUTTON_SCENE7, LaunchpadColorManager.LAUNCHPAD_COLOR_BLACK);
        this.setTrigger (LaunchpadControlSurface.LAUNCHPAD_BUTTON_SCENE8, LaunchpadColorManager.LAUNCHPAD_COLOR_BLACK);

        super.internalShutdown ();

        this.definition.resetMode (this);
    }


    /** {@inheritDoc} */
    @Override
    public void setTrigger (final int channel, final int cc, final int state)
    {
        if (!this.isPro () && (cc == LAUNCHPAD_BUTTON_SCENE1 || cc == LAUNCHPAD_BUTTON_SCENE2 || cc == LAUNCHPAD_BUTTON_SCENE3 || cc == LAUNCHPAD_BUTTON_SCENE4 || cc == LAUNCHPAD_BUTTON_SCENE5 || cc == LAUNCHPAD_BUTTON_SCENE6 || cc == LAUNCHPAD_BUTTON_SCENE7 || cc == LAUNCHPAD_BUTTON_SCENE8))
            this.output.sendNote (cc, state);
        else
            this.output.sendCC (cc, state);
    }


    /** {@inheritDoc} */
    @Override
    protected void flushHardware ()
    {
        super.flushHardware ();

        ((LaunchpadPadGrid) this.pads).flush ();
    }


    /**
     * Send sysex data to the launchpad.
     *
     * @param data The data without the header and closing byte
     */
    public void sendLaunchpadSysEx (final String data)
    {
        this.output.sendSysex (this.definition.getSysExHeader () + data + " F7");
    }


    /**
     * Is this device a Pro model with additional buttons?
     *
     * @return True if it is a pro version
     */
    public boolean isPro ()
    {
        return this.definition.isPro ();
    }


    private void handleSysEx (final String data)
    {
        final int [] byteData = StringUtils.fromHexStr (data);

        final DeviceInquiry deviceInquiry = new DeviceInquiry (byteData);
        if (deviceInquiry.isValid ())
            this.handleDeviceInquiryResponse (deviceInquiry);
    }


    /**
     * Handle the response of a device inquiry.
     *
     * @param deviceInquiry The parsed response
     */
    private void handleDeviceInquiryResponse (final DeviceInquiry deviceInquiry)
    {
        final int [] revisionLevel = deviceInquiry.getRevisionLevel ();
        if (revisionLevel.length == 4)
        {
            final String firmwareVersion = String.format ("%d%d%d%d", Integer.valueOf (revisionLevel[0]), Integer.valueOf (revisionLevel[1]), Integer.valueOf (revisionLevel[2]), Integer.valueOf (revisionLevel[3]));
            this.host.println ("Firmware version: " + (firmwareVersion.charAt (0) == '0' ? firmwareVersion.substring (1) : firmwareVersion));
        }
    }
}