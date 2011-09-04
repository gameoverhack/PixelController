/**
 * Copyright (C) 2011 Michael Vogt <michu@neophob.com>
 *
 * This file is part of PixelController.
 *
 * PixelController is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * PixelController is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with PixelController.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.neophob.sematrix.properties;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.lang.StringUtils;

import com.neophob.sematrix.glue.Collector;
import com.neophob.sematrix.glue.PresentSettings;
import com.neophob.sematrix.layout.BoxLayout;
import com.neophob.sematrix.layout.HorizontalLayout;
import com.neophob.sematrix.layout.Layout;
import com.neophob.sematrix.output.OutputDeviceEnum;

/**
 * load and save properties files
 * 
 * @author michu
 *
 */
public final class PropertiesHelper {

	private static Logger log = Logger.getLogger(PropertiesHelper.class.getName());
	
	private static PropertiesHelper instance = new PropertiesHelper();

	//define config files
	private static final String PRESENTS_FILENAME = "data/presents.led";
	private static final String CONFIG_FILENAME = "data/config.properties";
	
	//define common error messages
	private static final String ERROR_NO_DEVICES_CONFIGURATED = "No devices configured, illegal configuration!";
	private static final String ERROR_MULTIPLE_DEVICES_CONFIGURATED = "Multiple devices configured, illegal configuration!";
	
	private Properties config=null;
	
	private OutputDeviceEnum outputDeviceEnum = null;
	
	//output specific settings
	private List<Integer> i2cAddr=null;
	private List<DeviceConfig> lpdDevice=null;
	private List<ColorFormat> colorFormat=null;
	
	//how many output screens are used? needed to define layouts
	private int devicesInRow1 = 0;
	private int devicesInRow2 = 0;
	
	//Resolution of the output device
	private int deviceXResolution = 8;
	private int deviceYResolution = 8;
	
	/**
	 * 
	 */
	private PropertiesHelper() {
		config = new Properties();		
		try {
			InputStream input = Collector.getInstance().getPapplet().createInput(CONFIG_FILENAME);
			config.load(input);
						
			log.log(Level.INFO, "Config loaded, {0} entries", config.size());
		} catch (Exception e) {
			log.log(Level.SEVERE, "Failed to load Config", e);
			throw new IllegalArgumentException("Configuration error!", e);
		}

		int rainbowduinoDevices = parseI2cAddress();
		int pixelInvadersDevices = parseLpdAddress();
		int artnetDevices = parseArtNetDevices();
		int miniDmxDevices = parseMiniDmxDevices();

		//track how many output systems are enabled
		int enabledOutputs = 0;
		
		//track how many ouput devices are configured
		int totalDevices = 0;
		
		if (rainbowduinoDevices > 0) {
			enabledOutputs++;
			totalDevices = rainbowduinoDevices;
			log.log(Level.INFO, "found Rainbowduino device: "+totalDevices);
			this.outputDeviceEnum = OutputDeviceEnum.RAINBOWDUINO;
		}  
		if (pixelInvadersDevices > 0) {
			enabledOutputs++;
			totalDevices = pixelInvadersDevices;
			log.log(Level.INFO, "found PixelInvaders device: "+totalDevices);
			this.outputDeviceEnum = OutputDeviceEnum.LPD6803;
		}
		if (artnetDevices > 0) {
			enabledOutputs++;
			totalDevices = artnetDevices;
			log.log(Level.INFO, "found Artnet device: "+totalDevices);
			this.outputDeviceEnum = OutputDeviceEnum.ARTNET;
		}
		if (miniDmxDevices > 0) {
			enabledOutputs++;
			totalDevices = miniDmxDevices;
			log.log(Level.INFO, "found miniDMX device: "+totalDevices);
			this.outputDeviceEnum = OutputDeviceEnum.MINIDMX;
		} 
		
		if (enabledOutputs>1) {
			log.log(Level.SEVERE, ERROR_MULTIPLE_DEVICES_CONFIGURATED+": "+enabledOutputs);
			throw new IllegalArgumentException(ERROR_MULTIPLE_DEVICES_CONFIGURATED);
		}

		if (enabledOutputs==0 || totalDevices==0) {
			log.log(Level.SEVERE, ERROR_NO_DEVICES_CONFIGURATED);
			throw new IllegalArgumentException(ERROR_NO_DEVICES_CONFIGURATED);
		}
				
		//add default color format RGB is nothing is configured
		int nrOfColorFormat = getColorFormatFromCfg();
		if (nrOfColorFormat==0) {
			for (int i=0; i<totalDevices; i++) {
				colorFormat.add(ColorFormat.RBG);
			}
		}
	}
	
	/**
	 * 
	 * @return
	 */
	public static PropertiesHelper getInstance() {
		return instance;
	}

	/**
	 * 
	 * @param property
	 * @return
	 */
	private boolean parseBoolean(String property) {
		String rawConfig = config.getProperty(property);
		if (StringUtils.isNotBlank(rawConfig)) {
			try {
				return Boolean.parseBoolean(rawConfig);
			} catch (Exception e) {
				log.log(Level.WARNING, "Failed to parse {0}", rawConfig);
			}
		}
		return false;
	}
	
	/**
	 * get a int value from the config file
	 * @param property
	 * @return
	 */
	private int parseInt(String property) {
		String rawConfig = config.getProperty(property);
		if (StringUtils.isNotBlank(rawConfig)) {
			try {
				return Integer.parseInt(rawConfig);
			} catch (Exception e) {
				log.log(Level.WARNING, "Failed to parse {0}", rawConfig);
			}
		}
		return 0;		
	}


	/**
	 * 
	 * @param key
	 * @return
	 */
	public String getProperty(String key) {
		return config.getProperty(key);
	}

	/**
	 * 
	 * @param key
	 * @param defaultValue
	 * @return
	 */
	public String getProperty(String key, String defaultValue) {
		return config.getProperty(key, defaultValue);
	}
	
	
	/**
	 * 
	 */
	public void loadPresents() {
		Properties props = new Properties();
		try {
			InputStream input = Collector.getInstance().getPapplet().createInput(PRESENTS_FILENAME);
			List<PresentSettings> presents = Collector.getInstance().getPresent();
			props.load(input);
			String s;
			int count=0;
			for (int i=0; i<Collector.NR_OF_PRESENT_SLOTS; i++) {
				s=props.getProperty(""+i);
				if (StringUtils.isNotBlank(s)) {
					presents.get(i).setPresent(s.split(";"));
					count++;
				}
			}
			log.log(Level.INFO,
					"Loaded {0} presents from file {1}"
					, new Object[] { count, PRESENTS_FILENAME });
		} catch (Exception e) {
			log.log(Level.WARNING,
					"Failed to load {0}, Error: {1}"
					, new Object[] { PRESENTS_FILENAME, e });
		}
	}
		
	/**
	 * 
	 */
	public void savePresents() {
		Properties props = new Properties();
		List<PresentSettings> presents = Collector.getInstance().getPresent();
		int idx=0;
		for (PresentSettings p: presents) {
			props.setProperty( ""+idx, p.getSettingsAsString() );
			idx++;
		}
		
		try {
			OutputStream output = Collector.getInstance().getPapplet().createOutput(PRESENTS_FILENAME);
			props.store(output, "Visual Daemon presents file");
			log.log(Level.INFO,
					"Presents saved as {0}"
					, new Object[] { PRESENTS_FILENAME });
		} catch (Exception e) {
			log.log(Level.WARNING,
					"Failed to save {0}, Error: {1}"
					, new Object[] { PRESENTS_FILENAME, e });
		}
	}

	/**
	 * 
	 * @return
	 */
	private int parseLpdAddress() {
		lpdDevice = new ArrayList<DeviceConfig>();
		
		String value = config.getProperty("layout.row1");
		if (StringUtils.isNotBlank(value)) {
			for (String s: value.split(",")) {
				try {
					DeviceConfig cfg = DeviceConfig.valueOf(s);
					lpdDevice.add(cfg);
					devicesInRow1++;
				} catch (Exception e) {
					log.log(Level.WARNING,
							"Failed to parse {0}", s);

				}
			}
		}

		value = config.getProperty("layout.row2");
		if (StringUtils.isNotBlank(value)) {
			for (String s: value.split(",")) {
				try {
					DeviceConfig cfg = DeviceConfig.valueOf(s);
					lpdDevice.add(cfg);
					devicesInRow2++;				
				} catch (Exception e) {
					log.log(Level.WARNING,
							"Failed to parse {0}", s);

				}
			}
		}

		return lpdDevice.size();
	}
	
	/**
	 * get the size of the software emulated matrix
	 * @return the size or -1 if nothing was defined
	 */
	public int getLedPixelSize() {
		int ret=-1;
		
		String tmp = config.getProperty("led.pixel.size");
		try {
			ret = Integer.parseInt(tmp);
		} catch (NumberFormatException e) {
			// TODO: handle exception
		}
		return ret;
		
	}
	
	/**
	 * 
	 * @return
	 */
	private int getColorFormatFromCfg() {
		colorFormat = new ArrayList<ColorFormat>();
		String rawConfig = config.getProperty("panel.color.order");
		
		if (StringUtils.isNotBlank(rawConfig)) {
			for (String s: rawConfig.split(",")) {
				try {
					ColorFormat cf = ColorFormat.valueOf(s);
					colorFormat.add(cf);					
				} catch (Exception e) {
					log.log(Level.WARNING, "Failed to parse {0}", s);
				}
			}			
		}
		
		return colorFormat.size();
	}
	
	/**
	 * 
	 * @return
	 */
	private int parseI2cAddress() {
		i2cAddr = new ArrayList<Integer>();
		
		String rawConfig = config.getProperty("layout.row1.i2c.addr");
		if (StringUtils.isNotBlank(rawConfig)) {
			for (String s: rawConfig.split(",")) {
				i2cAddr.add( Integer.parseInt(s));
				devicesInRow1++;
			}
		}
		rawConfig = config.getProperty("layout.row2.i2c.addr");
		if (StringUtils.isNotBlank(rawConfig)) {
			for (String s: rawConfig.split(",")) {
				i2cAddr.add( Integer.parseInt(s));
				devicesInRow2++;
			}
		}
		
		return i2cAddr.size();
	}

	/**
	 * get configured artnet ip
	 * @return
	 */
	public String getArtNetIp() {
		return config.getProperty("artnet.ip");
	}
	
	/**
	 * 
	 * @return
	 */
	private int parseArtNetDevices() {
		//minimal ip length 1.1.1.1
		if (StringUtils.length(getArtNetIp())>6) {
			devicesInRow1=1;
			return 1;
		}
		
		return 0;
	}

	/**
	 * 
	 * @return
	 */
	private int parseMiniDmxDevices() {
		if (parseMiniDmxDevicesX()>0 && parseMiniDmxDevicesY()>0) {
			this.devicesInRow1=1;
			this.deviceXResolution = parseMiniDmxDevicesX();
			this.deviceYResolution = parseMiniDmxDevicesY();
			return 1;
		}
		return 0;
	}
	
	/**
	 * 
	 * @return
	 */
	public int parseMiniDmxDevicesX() {
		return parseInt("minidmx.resolution.x");
	}

	/**
	 * 
	 * @return
	 */
	public int parseMiniDmxDevicesY() {
		return parseInt("minidmx.resolution.y");
	}
	
	/**
	 * 
	 * @return
	 */
	public boolean startRandommode() {
		return parseBoolean("startup.in.randommode");
	}
	
	/**
	 * 
	 * @return
	 */
	public int getNrOfScreens() {
		return devicesInRow1+devicesInRow2;
	}
	
	
	/**
	 * 
	 * @return
	 */
	public Layout getLayout() {
		if (devicesInRow2>0) {
			return new BoxLayout(devicesInRow1, devicesInRow2);
		}
	
		return new HorizontalLayout(devicesInRow1, devicesInRow2);
	}

	/**
	 * 
	 * @return i2c address for rainbowduino devices
	 */
	public List<Integer> getI2cAddr() {
		return i2cAddr;
	}

	/**
	 * 
	 * @return options to display lpd6803 displays
	 */
	public List<DeviceConfig> getLpdDevice() {
		return lpdDevice;
	}

	/**
	 * 
	 * @return
	 */
	public List<ColorFormat> getColorFormat() {
		return colorFormat;
	}
	
	/**
	 * @return the configured output device
	 */
	public OutputDeviceEnum getOutputDevice() {
		return this.outputDeviceEnum;
	}

	/**
	 * 
	 * @return
	 */
	public int getDeviceXResolution() {
		return deviceXResolution;
	}

	/**
	 * 
	 * @return
	 */
	public int getDeviceYResolution() {
		return deviceYResolution;
	}
	
	
}
