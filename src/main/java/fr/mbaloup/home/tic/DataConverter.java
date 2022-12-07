package fr.mbaloup.home.tic;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
import java.util.Objects;

public class DataConverter {

	// see https://www.enedis.fr/sites/default/files/Enedis-NOI-CPT_54E.pdf §6.2.1.1
	public static long fromDateToMillis(String date) {
		Objects.requireNonNull(date, "date cannot be null");
		if (date.length() != 13)
			throw new IllegalArgumentException("date must have exactly 13 characters");
		
		int i = 0;
		char season = date.charAt(i++);
		String offsetStr = Character.toLowerCase(season) == 'e' ? "+02:00" : "+01:00";
		String yearStr = date.substring(i, i += 2);
		String monthStr = date.substring(i, i += 2);
		String dayStr = date.substring(i, i += 2);
		String hourStr = date.substring(i, i += 2);
		String minStr = date.substring(i, i += 2);
		String secStr = date.substring(i, i + 2);
		
		TemporalAccessor ta = DateTimeFormatter.ISO_OFFSET_DATE_TIME.parse("20"+yearStr+"-"+monthStr+"-"+dayStr+"T"+hourStr+":"+minStr+":"+secStr+offsetStr);
		return Instant.from(ta).toEpochMilli();
	}
	

}
