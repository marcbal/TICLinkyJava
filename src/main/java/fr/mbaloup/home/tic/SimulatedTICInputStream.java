package fr.mbaloup.home.tic;

import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.Random;

public class SimulatedTICInputStream extends PipedInputStream {
	
	PipedOutputStream out = new PipedOutputStream();
	
	Thread th;
	
	public SimulatedTICInputStream() throws IOException {
		super(2048);
		connect(out);
		th = new Thread(this::run, "Simulated TIC Thread");
		th.setDaemon(true);
		th.start();
	}
	
	private boolean closed = false;
	
	private int indexHC = 0;
	private int indexHP = 0;
	private long lastTime = System.currentTimeMillis();
	private long wattSecondsCumul = 0; // 3600 Ws = 1 Wh
	
	private final Random rand = new Random();
	
	private int count = 0;
	
	private void run() {
		while (!closed) {
			
			Calendar cal = new GregorianCalendar();
			boolean hc = (cal.get(Calendar.HOUR_OF_DAY) < 8);
			int power = cal.get(Calendar.MINUTE) % 2 == 0 ? 2500 : 200;
			float r = rand.nextFloat();
			power += r < 0.2 ? 10 : r < 0.4 ? -10 : 0;
			
			long newT = cal.getTimeInMillis();
			wattSecondsCumul += power * (newT - lastTime) / 1000;
			if (wattSecondsCumul > 3600) {
				int indexIncr = (int) (wattSecondsCumul / 3600);
				wattSecondsCumul -= indexIncr * 3600L;
				if (hc)
					indexHC += indexIncr;
				else
					indexHP += indexIncr;
			}
			lastTime = newT;
			
			write(TICRawDecoder.FRAME_START);
			writeInfoGroup("ADCO", "DUMMY");
			writeInfoGroup("OPTARIF", "HC..");
			writeInfoGroup("ISOUSC", "30");
			writeInfoGroup("HCHC", String.format("%08d", indexHC));
			writeInfoGroup("HCHP", String.format("%08d", indexHP));
			writeInfoGroup("PTEC", hc ? "HC.." : "HP..");
			writeInfoGroup("IINST", String.format("%03d", Math.round(power / (float) 200)));
			writeInfoGroup("IMAX", "090");
			writeInfoGroup("PAPP", String.format("%05d", power));
			writeInfoGroup("HHPHC", "A");
			writeInfoGroup("MOTDETAT", String.format("%06d", (count++) % 1000000));
			write(TICRawDecoder.FRAME_END);
		}
	}
	
	private void writeInfoGroup(String key, String value) {
		try {
			Thread.sleep(8);
		} catch (InterruptedException e) {
			throw new RuntimeException(e);
		}
		write(TICRawDecoder.DATASET_START);
		write(key);
		write(TICRawDecoder.SEP_SP);
		write(value);
		write(TICRawDecoder.SEP_SP);
		write(TICRawDecoder.checksum(key + TICRawDecoder.SEP_SP + value + TICRawDecoder.SEP_SP));
		write(TICRawDecoder.DATASET_END);
	}
	
	private void write(String s) {
		for (char c : s.toCharArray())
			write(c);
	}
	
	private synchronized void write(char c) {
		if (closed)
			return;
		try {
			Thread.sleep(2);
			out.write(c);
		} catch (InterruptedException | IOException e) {
			throw new RuntimeException(e);
		}
	}
	
	@Override
	public synchronized void close() throws IOException {
		closed = true;
		super.close();
	}

}
