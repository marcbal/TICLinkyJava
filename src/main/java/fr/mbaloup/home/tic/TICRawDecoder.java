package fr.mbaloup.home.tic;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import org.rapidoid.log.Log;

public class TICRawDecoder extends Thread {

	static final char FRAME_START = 0x02;
	static final char FRAME_END = 0x03;
	static final char TRANSMISSION_END = 0x04;
	static final char DATASET_START = 0x0A;
	static final char DATASET_END = 0x0D;
	static final char SEP_SP = 0x20;
	static final char SEP_HT = 0x09;
	
	static final String INPUT_PATH = "/dev/ttyTIC";
	
	static final int CORRUPTION_MAX_LEVEL = 10; // max number of corruption detected while reading raw input
	static final long CORRUPTION_LEVEL_DECAY_INTERVAL = 5000; // interval in ms between each corruption level decrement
	
	private InputStream input;
	
	private int corruptedDataCount = 0;
	private long lastCorruptedData = System.currentTimeMillis();
	
	private final BlockingQueue<DataFrame> outQueue = new LinkedBlockingQueue<>(10000);
	
	
	public TICRawDecoder() throws IOException {
		super("TIC Input Thread");
		configureInput();
		start();
	}
	
	
	
	private void configureInput() throws IOException {
		input = new FileInputStream(INPUT_PATH);
		//input = new SimulatedTICInputStream();
	}
	
	
	private void signalCorruptedInput(String desc) {
		corruptedDataCount++;
		lastCorruptedData = System.currentTimeMillis();
		Log.warn("Raw input corruption detected (" + corruptedDataCount + "): " + desc);
	}
	
	private boolean checkCorruptedInputStatus() throws IOException {
		if (corruptedDataCount > 0) {
			if (lastCorruptedData < System.currentTimeMillis() - 5000) {
				corruptedDataCount--;
				lastCorruptedData = System.currentTimeMillis();
			}
		}
		
		if (corruptedDataCount > 10) {
			Log.warn("Raw input corruption is too high, reopening the input stream... ");
			try {
				input.close();
				try { Thread.sleep(2000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
				configureInput();
			}
			finally {
				corruptedDataCount = 0;
			}
			return true;
			
		}
		
		return false;
	}
	
	
	@Override
	public void run() {
		try {
			Map<String, DataSet> frameData = null;
			long frameTime = -1;
			StringBuilder dsBuffer = new StringBuilder(32);
			boolean processingDS = false;

			Log.info("Decoder is now running.");
			
			for(;;) {
				if (checkCorruptedInputStatus()) {
					frameData = null;
					dsBuffer.setLength(0);
					processingDS = false;
				}
				
				
				
				char c = (char) input.read();
				
				// handle new frame
				if (c == FRAME_START) {
					frameData = new LinkedHashMap<>();
					frameTime = System.currentTimeMillis();
					continue;
				}
				// skip all data encountered before our first FRAME_START
				if (frameData == null) 
					continue;
				
				// handle frame end
				if (c == FRAME_END || c == TRANSMISSION_END) {
					if (!frameData.isEmpty()) {
						outQueue.add(new DataFrame(frameTime, frameData));
					}
					else {
						signalCorruptedInput("FRAME_END or TRANSMISSION_END encountered with not data frame registered.");
					}
					frameData = null;
					continue;
				}
				
				// infogroup start
				if (c == DATASET_START) {
					if (processingDS) // should not happened
						signalCorruptedInput("DATASET_START encountered while already decoding an info group.");
					processingDS = true;
					dsBuffer.setLength(0);
					continue;
				}
				// infogroup end
				if (c == DATASET_END) {
					if (dsBuffer.length() >= 3) {
						try {
							String[] dsData = toDataSetStrings(dsBuffer.toString());
							if (dsData != null) {
								if (dsData.length == 2) {
									frameData.put(dsData[0], new DataSet(null, dsData[1]));
								}
								else if (dsData.length == 3) {
									frameData.put(dsData[0], new DataSet(DataConverter.fromDateToMillis(dsData[1]), dsData[2]));
								}
								else {
									signalCorruptedInput("Data set content corrupted (invalid format).");
								}
							}
						} catch (Exception e) {
							signalCorruptedInput("Error while decoding data set: " + e.getMessage());
						}
					}
					else {
						signalCorruptedInput("DATASET_END encountered too soon.");
					}
					processingDS = false;
					continue;
				}
				
				// skip all data outside infogroups
				if (!processingDS) {
					signalCorruptedInput("Expected DATASET_START, but received regular character.");
					continue;
				}
				
				dsBuffer.append(c);
			}
		} catch (IOException e) {
			Log.error("Error while reading raw TIC input", e);
			System.exit(1);
		}
	}
	
	
	
	
	
	
	
	
	int checksumMode = 0;
	char prevSeparator = 0x00;
	
	private String[] toDataSetStrings(String buff) {
		
		char checksum = buff.charAt(buff.length() - 1);
		char separator = buff.charAt(buff.length() - 2);
		
		if (separator != SEP_SP && separator != SEP_HT) {
			signalCorruptedInput("Invalid info group separator in buffer " + hexdump(buff));
			return null;
		}
		
		// check separator
		if (prevSeparator == 0x00)
			prevSeparator = separator;
		else if (separator != prevSeparator) {
			signalCorruptedInput("Received separator is not the same as previously received. Buffer is " + hexdump(buff));
			return null;
		}
		
		// check checksum
		if (checksumMode == 0) {
			char cs1 = checksum(buff.substring(0, buff.length() - 2));
			char cs2 = checksum(buff.substring(0, buff.length() - 1));
			if (checksum == cs1) {
				if (checksum != cs2) {
					checksumMode = 1;
				}
				// else {} // ignored, because in this case, both checksum are equals to the received checksum
			}
			else {
				if (checksum == cs2) {
					checksumMode = 2;
				}
				else {
					signalCorruptedInput("Invalid checksum. Received " + hex(checksum) + " but expected either " + hex(cs1) + " (mode 1) or " + hex(cs2) + " (mode 2). Buffer is " + hexdump(buff));
					return null;
				}
			}
		}
		else if (checksumMode == 1) {
			char cs1 = checksum(buff.substring(0, buff.length() - 2));
			if (checksum != cs1) {
				signalCorruptedInput("Invalid checksum. Received " + hex(checksum) + " but expected " + hex(cs1) + " (mode 1). Buffer is " + hexdump(buff));
				return null;
			}
		}
		else if (checksumMode == 2) {
			char cs2 = checksum(buff.substring(0, buff.length() - 1));
			if (checksum != cs2) {
				signalCorruptedInput("Invalid checksum. Received " + hex(checksum) + " but expected " + hex(cs2) + " (mode 2). Buffer is " + hexdump(buff));
				return null;
			}
		}
		
		// separate key, value and eventually the time data
		return buff.substring(0, buff.length() - 2).split("" + separator, separator == SEP_SP ? 2 : 3);
	}
	
	public static char checksum(String s) {
		int cs = 0;
		for (char c : s.toCharArray()) {
			cs += c;
		}
		return (char) ((cs & 0x3F) + 0x20);
	}
	

	
	
	
	
	
	
	
	
	
	
	
	
	
	


	private static String hex(char c) {
		return String.format("0x%02x", (int)c);
	}
	
	private static String hexdump(String s) {
		StringBuilder sb = new StringBuilder();
		for (char c : s.toCharArray())
			sb.append(String.format("%02x ", (int)c));
		sb.append('|');
		for (char c : s.toCharArray())
			sb.append((c >= ' ' && c < 0x7f) ? c : '.');
		sb.append('|');
		return sb.toString();
	}
	
	
	
	
	
	
	
	
	
	
	public DataFrame getNextDataFrame() throws InterruptedException {
		if (outQueue.size() > 3) {
			Log.warn("TICDecoder queue is growing (size=" +  outQueue.size() + ")");
		}
		return outQueue.take();
	}
}
