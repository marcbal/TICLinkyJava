package fr.mbaloup.home;

import java.io.IOException;

import org.rapidoid.log.Log;

import fr.mbaloup.home.tic.TICDataDispatcher;
import fr.mbaloup.home.tic.TICRawDecoder;

public class Main {

	public static TICDataDispatcher tic;
	public static void main(String[] args) throws IOException, InterruptedException {

		Log.info("Initializing TIC raw decoder...");
		TICRawDecoder decoder = new TICRawDecoder();
		
		Log.info("Initializing TIC data dispatcher...");
		tic = new TICDataDispatcher(decoder);
	}
	
	
	
	
	
	
}
