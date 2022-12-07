package fr.mbaloup.home.util;

import java.util.Calendar;
import java.util.GregorianCalendar;

public class TimeUtil {

	
	public static boolean isDifferentDay(long c1, long c2) {
		return isDifferentDay(getCalendarOfTime(c1), getCalendarOfTime(c2));
	}
	
	public static boolean isDifferentDay(Calendar c1, Calendar c2) {
		return getDayOfMonth(c1) != getDayOfMonth(c2)
				|| getMonth(c1) != getMonth(c2)
				|| getYear(c1) != getYear(c2);
	}

	public static int getNbDayInMonth(Calendar cal) {
		switch(getMonth(cal)) {
		case Calendar.JANUARY:
		case Calendar.MARCH:
		case Calendar.MAY:
		case Calendar.JULY:
		case Calendar.AUGUST:
		case Calendar.OCTOBER:
		case Calendar.DECEMBER:
			return 31;
		case Calendar.FEBRUARY:
			return ((GregorianCalendar) cal).isLeapYear(getYear(cal)) ? 29 : 28;
		default:
			return 30;
		}
	}

	public static int getDayOfMonth(Calendar cal) {
		return cal.get(Calendar.DAY_OF_MONTH);
	}
	public static int getMonth(Calendar cal) {
		return cal.get(Calendar.MONTH);
	}
	public static int getYear(Calendar cal) {
		return cal.get(Calendar.YEAR);
	}
	
	public static void setMidnight(Calendar cal) {
		cal.set(Calendar.HOUR_OF_DAY, 0);
		cal.set(Calendar.MINUTE, 0);
		cal.set(Calendar.SECOND, 0);
		cal.set(Calendar.MILLISECOND, 0);
	}
	
	public static Calendar getCalendarOfTime(long time) {
		Calendar cal = new GregorianCalendar();
		cal.setTimeInMillis(time);
		return cal;
	}
}
