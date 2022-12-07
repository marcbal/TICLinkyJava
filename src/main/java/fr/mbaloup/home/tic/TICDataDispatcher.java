package fr.mbaloup.home.tic;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

import org.rapidoid.log.Log;

import fr.mbaloup.home.mqtt.MQTTSender;
import fr.mbaloup.home.tic.DataFrame.DataSetExecutor;
import fr.mbaloup.home.tic.TICDataDispatcher.DayScheduleTimeSlot.PhysicalRelayChange;

public class TICDataDispatcher extends Thread {

	

	private final TICRawDecoder decoder;
	
	public TICDataDispatcher(TICRawDecoder ticRaw) {
		super("TIC Dispatcher Thread");
		decoder = ticRaw;
		start();
	}
	
	
	@Override
	public void run() {
		for (;;) {
			try {
				onDataUpdate(decoder.getNextDataFrame());
			} catch (InterruptedException e) {
				return;
			} catch (Exception e) {
				Log.error("Error while handling a data frame", e);
			}
		}
	}
	
	
	
	
	
	
	
	private synchronized void onDataUpdate(DataFrame df) {
		
		if (df.data.containsKey("ADCO"))
			updateHistorical(df);
		else
			updateStandard(df);
		
		
		
		
		lastUpdates.addLast(System.currentTimeMillis());
		while (lastUpdates.size() > 32)
			lastUpdates.removeFirst();
		
		if (lastUpdates.size() >= 2)
			avgUpdateInterval = (lastUpdates.getLast() - lastUpdates.getFirst()) / (lastUpdates.size() - 1);
	}
	
	
	private void updateHistorical(DataFrame df) {
		systTime = df.time;
		df.ifKeyPresent("ADCO", (k, ds, t) -> {
			updateSerialNumber(ds.data);
			updateTICVersion(null);
		});
		df.ifKeyPresent("OPTARIF", (k, ds, t) -> updateSubscribedOption(k, ds.data));
		df.ifKeyPresent("ISOUSC", (k, ds, t) -> updateHistoricalSubscribedIntensity(ds.data));
		
		DataSetExecutor indexExecutor = (k, ds, t) -> updateIndexHist(k, ds.data);
		df.ifKeyPresent("BASE", indexExecutor);
		df.ifKeyPresent("HCHC", indexExecutor);
		df.ifKeyPresent("HCHP", indexExecutor);
		df.ifKeyPresent("EJPHN", indexExecutor);
		df.ifKeyPresent("EJPHPM", indexExecutor);
		df.ifKeyPresent("BBRHCJB", indexExecutor);
		df.ifKeyPresent("BBRHPJB", indexExecutor);
		df.ifKeyPresent("BBRHCJW", indexExecutor);
		df.ifKeyPresent("BBRHPJW", indexExecutor);
		df.ifKeyPresent("BBRHCJR", indexExecutor);
		df.ifKeyPresent("BBRHPJR", indexExecutor);
		
		histMobilePointNotice = df.data.containsKey("PEJP");
		df.ifKeyPresent("PTEC", (k, ds, t) -> updateCurrIndexHist(ds.data));
		df.ifKeyPresent("DEMAIN", (k, ds, t) -> updateDEMAIN(ds.data));
		df.ifKeyPresent("IINST", (k, ds, t) -> updateRMSCurrent(ds.data));
		overPowerConsumption = df.data.containsKey("ADPS");
		df.ifKeyPresent("IMAX", (k, ds, t) -> {
			/* unclear what represent this data, except "Intensité maximale appelée".
			 * On Linky electric meter with historical TIC mode,
			 * the received value is always 90 A.
			 */
		});
		df.ifKeyPresent("PAPP", (k, ds, t) -> updateApparentPower(ds.data));
		df.ifKeyPresent("HHPHC", (k, ds, t) -> updateHHPHC(ds.data));
		df.ifKeyPresent("MOTDETAT", (k, ds, t) -> updateMOTDETAT(ds.data));
	}
	
	
	
	
	private void updateStandard(DataFrame df) {
		df.ifKeyPresent("DATE", (k, ds, t) -> updateTICTimeStd(t, ds.time));
		df.ifKeyPresent("ADSC", (k, ds, t) -> updateSerialNumber(ds.data));
		df.ifKeyPresent("VTIC", (k, ds, t) -> updateTICVersion(ds.data));
		df.ifKeyPresent("NGTF", (k, ds, t) -> updateSubscribedOption(k, ds.data));
		df.ifKeyPresent("NTARF", (k, ds, t) -> {
			// NTARF must be updated before LTARF
			updateCurrentIndexId(ds.data);
			df.ifKeyPresent("LTARF", (k2, ds2, t2) -> updateNameOfCurrentIndex(ds2.data));
		});
		
		df.ifKeyPresent("EAST", (k, ds, t) -> updateTotalIndexStd(ds.data));
		
		DataSetExecutor indexExecutor = (k, ds, t) -> updateIndexStd(k.substring(4), ds.data);
		df.ifKeyPresent("EASF01", indexExecutor);
		df.ifKeyPresent("EASF02", indexExecutor);
		df.ifKeyPresent("EASF03", indexExecutor);
		df.ifKeyPresent("EASF04", indexExecutor);
		df.ifKeyPresent("EASF05", indexExecutor);
		df.ifKeyPresent("EASF06", indexExecutor);
		df.ifKeyPresent("EASF07", indexExecutor);
		df.ifKeyPresent("EASF08", indexExecutor);
		df.ifKeyPresent("EASF09", indexExecutor);
		df.ifKeyPresent("EASF10", indexExecutor);
		
		DataSetExecutor distIndexExecutor = (k, ds, t) -> updateDistributorIndex(k.substring(4), ds.data);
		df.ifKeyPresent("EASD01", distIndexExecutor);
		df.ifKeyPresent("EASD02", distIndexExecutor);
		df.ifKeyPresent("EASD03", distIndexExecutor);
		df.ifKeyPresent("EASD04", distIndexExecutor);
		
		df.ifKeyPresent("IRMS1", (k, ds, t) -> updateRMSCurrent(ds.data));
		df.ifKeyPresent("URMS1", (k, ds, t) -> updateRMSVoltage(ds.data));
		df.ifKeyPresent("PREF", (k, ds, t) -> updateRefPower(ds.data));
		df.ifKeyPresent("PCOUP", (k, ds, t) -> updateCutPower(ds.data));
		df.ifKeyPresent("SINSTS", (k, ds, t) -> updateApparentPower(ds.data));
		df.ifKeyPresent("SMAXSN", (k, ds, t) -> updateMaxPowerToday(ds.time, ds.data));
		df.ifKeyPresent("SMAXSN-1", (k, ds, t) -> updateMaxPowerYesterday(ds.time, ds.data));
		df.ifKeyPresent("UMOY1", (k, ds, t) -> updateAvgVoltage(ds.time, ds.data));
		df.ifKeyPresent("PRM", (k, ds, t) -> updatePRM(ds.data));
		df.ifKeyPresent("NJOURF", (k, ds, t) -> updateProviderCalDayToday(ds.data));
		df.ifKeyPresent("NJOURF+1", (k, ds, t) -> updateProviderCalDayTomorrow(ds.data));
		df.ifKeyPresent("MSG1", (k, ds, t) -> updateMessage1(ds.data));
		updateMessage2(df.data.containsKey("MSG2") ? df.data.get("MSG2").data : null);
		df.ifKeyPresent("STGE", (k, ds, t) -> updateStatusRegister(ds.data)); // 4 last bits ignored for the moment
		df.ifKeyPresent("RELAIS", (k, ds, t) -> updateRelaysStatus(ds.data));
		df.ifKeyPresent("PJOURF+1", (k, ds, t) -> updateNextDayScheduling(ds.data));
		
		
		
	}
	
	
	
	
	

	private final LinkedList<Long> lastUpdates = new LinkedList<>();
	private long avgUpdateInterval = -1;
	/**
	 * The average time interval between each consecutive data frame received from the TIC.
	 * The average is measured using the 32 last data framesm
	 * @return A time interval, in ms.
	 */
	public synchronized long getAverageUpdateInterval() {
		return avgUpdateInterval;
	}
	

	/**
	 * The time of when the last dataframe was fully received by the system, according to the system clock.
	 * <p>
	 * To have the time of the dataframe according to the electric meter, use {@link #getTICTime()}.
	 * To hae the time of when the first byte of the dataframe was received, use {@link #getFrameSystemTime()}.
	 * @return the millisecond epoch time of when the last dataframe was fully received by the system.
	 */
	public synchronized long getLastUpdateTime() {
		return lastUpdates.isEmpty() ? -1 : lastUpdates.getLast();
	}
	

	
	
	
	private Long systTime = null;
	private Long ticTime = null;
	private Long rawTICTime = null;
	private void updateTICTimeStd(long newSystTime, long newTICTime) {
		rawTICTime = newTICTime;
		
		/* Two consecutive data frame may have the same DATE value if the interval between 2 dataframe is below 1 second.
		 * In this case, this is necessary to determine a time closer to the real time (in the counter) to avoid bad
		 * interpretation of the data in subsequent processing (storage, display, ...).
		 */
		long ticTimeMin = newTICTime;
		long ticTimeMax = newTICTime+999;
		
		if (systTime == null) { // no previous data
			if (newSystTime > ticTimeMin && newSystTime <= ticTimeMax) {
				newTICTime = newSystTime;
			}
			else if (newSystTime > ticTimeMax) {
				newTICTime = ticTimeMax;
			}
		}
		else {
			long interval = newSystTime - systTime;
			long estimatedNewPreciseTICTime = ticTime + interval;
			if (estimatedNewPreciseTICTime > ticTimeMin && estimatedNewPreciseTICTime <= ticTimeMax) {
				newTICTime = estimatedNewPreciseTICTime;
			}
			else if (estimatedNewPreciseTICTime > ticTimeMax) {
				newTICTime = ticTimeMax;
			}
		}
		
		systTime = newSystTime;
		ticTime = newTICTime;
	}
	/**
	 * The time of when the first byte of the last dataframe was received, according to the system clock.
	 * <p>
	 * To have the time of the dataframe according to the electric meter, use {@link #getTICTime()}.
	 * @return the millisecond epoch time of when the first byte of the last dataframe was received.
	 */
	public synchronized Long getFrameSystemTime() { return systTime; }
	/**
	 * The time of the dataframe, according to the electric meter.
	 * <p>
	 * The time is received from the meter with a precision of a second. The value with millisecond precision
	 * is estimated using the time interval between the current and the last dataframe, and using
	 * the system clock.
	 * If you want to have the time, as declared by the TIC dataframe, use {@link #getRawTICTime()}.
	 * <p>
	 * The TIC time may have shifted from the real time. In this case, {@link #isInternalClockBad()} will return true.
	 * <p>
	 * To have the time of the dataframe according to the system clock, use {@link #getFrameSystemTime()}.
	 * <p>
	 * <ul>
	 * 	<li>Historical mode: not provided.</li>
	 * 	<li>Standard mode: DATE field.</li>
	 * </ul>
	 * @return the millisecond epoch time of the last dataset according to the electric meter.
	 */
	public synchronized Long getTICTime() { return ticTime; }
	/**
	 * The time of the dataframe, according to the electric meter.
	 * <p>
	 * The time is received from the meter with a precision of a second.
	 * If you want to have the time with millisecond precision, estimated using the system time, use {@link #getTICTime()}.
	 * <p>
	 * The TIC time may have shifted from the real time. In this case, {@link #isInternalClockBad()} will return true.
	 * <p>
	 * To have the time of the dataframe according to the system clock, use {@link #getFrameSystemTime()}.
	 * <p>
	 * <ul>
	 * 	<li>Historical mode: not provided.</li>
	 * 	<li>Standard mode: DATE field.</li>
	 * </ul>
	 * @return the millisecond epoch time of the last dataset according to the electric meter.
	 */
	public synchronized Long getRawTICTime() { return rawTICTime; }
	
	
	
	private String TICVersion = null;
	private void updateTICVersion(String v) { TICVersion = v; }
	/**
	 * Specification version of the TIC protocol.
	 * <p>
	 * <ul>
	 * 	<li>Historical mode: not provided.</li>
	 * 	<li>Standard mode: VTIC field.</li>
	 * </ul>
	 * This current data dispatcher supports the protocol {@code 02}.
	 * 
	 * @return the specification version of the TIC protocol, or {@code null} if the TIC is in historical mode.
	 */
	public synchronized String getTICVersion() { return TICVersion; }
	
	
	
	private String serialNumber = null;
	private void updateSerialNumber(String a) { serialNumber = a; }
	/**
	 * Serial number of the electric meter, as written physically on the front cover (except the 2 last digits).
	 * <p>
	 * <ul>
	 * 	<li>Historical mode: ADCO field.</li>
	 * 	<li>Standard mode: ADCS field.</li>
	 * </ul>
	 * @return the serial number of the electric meter.
	 */
	public synchronized String getSerialNumber() { return serialNumber; }
	public synchronized String getSerialNumberManufacturerCode() {
		return serialNumber == null ? null : serialNumber.substring(0, 2);
	}
	public synchronized Integer getSerialNumberManufactureYear() {
		return serialNumber == null ? null : (Integer.parseInt(serialNumber.substring(2, 4)) + 2000);
	}
	public synchronized String getSerialNumberMeterType() {
		return serialNumber == null ? null : serialNumber.substring(4, 6);
	}
	public synchronized String getSerialNumberUniquePart() {
		return serialNumber == null ? null : serialNumber.substring(6);
	}
	
	
	
	
	private String prm = null;
	private void updatePRM(String a) { prm = a; }
	/**
	 * Serial number of the electric meter, as written physically on the front cover (except the 2 last digits).
	 * <p>
	 * <ul>
	 * 	<li>Historical mode: not provided.</li>
	 * 	<li>Standard mode: PRM field.</li>
	 * </ul>
	 * @return the serial number of the electric meter.
	 */
	public synchronized String getPRM() { return prm; }
	
	
	
	
	
	private String subscribedOption = null;
	private void updateSubscribedOption(String k, String o) {
		if      (k.equals("OPTARIF") && o.equals("HC.."))
			subscribedOption = "H PLEINE/CREUSE";
		else if (k.equals("OPTARIF") && o.equals("EJP."))
			subscribedOption = "EJP";
		else
			subscribedOption = o.trim();
	}
	/**
	 * The name or the id of the currently active subscription option.
	 * <p>
	 * Some example of price schedule option are constant price, peak/off-peak time prices, ...
	 * <p>
	 * <ul>
	 * 	<li>Historical mode: OPTARIF field, with standardized values: {@code BASE}, {@code HC} or {@code EJP}.</li>
	 * 	<li>Standard mode: NGTF field, the content being a non-standardized display name.</li>
	 * </ul>
	 * @return the price schedule option subscribed by the consumer.
	 */
	public synchronized String getSubscribedOption() { return subscribedOption; }
	
	
	
	private Integer subscribedIntensity = null;
	private void updateHistoricalSubscribedIntensity(String i) {
		subscribedIntensity = Integer.parseInt(i);
		
		Integer oldCutPower = cutPower;
		referencePower = cutPower = subscribedIntensity * 200;
		
		if (!Objects.equals(cutPower, oldCutPower))
			MQTTSender.CUT_POWER.publish(cutPower);
	}
	/**
	 * The subscribed intensity.
	 * <p>
	 * <ul>
	 * 	<li>Historical mode: ISOUSC field.</li>
	 * 	<li>Standard mode: not provided directly, but we use {@code getReferencePower() / 200} to set this value.</li>
	 * </ul>
	 * @return the subscribed intensity, in Ampere.
	 * @deprecated the consumption limit is provided in the contract in term of apparent power (kVA), not intensity (A).
	 * Also, the transmitted intensity (in both historical and standard TIC mode) is not accurate since
	 * it is based on the assumption that the voltage is always 200 V.
	 */
	@Deprecated
	public synchronized Integer getSubscribedIntensity() { return subscribedIntensity; }
	
	
	
	private Integer referencePower = null;
	private void updateRefPower(String pRef) {
		referencePower = Integer.parseInt(pRef) * 1000;
		subscribedIntensity = referencePower / 200;
	}
	/**
	 * The subscribed maximum apparent power.
	 * <p>
	 * <ul>
	 * 	<li>Historical mode: not provided directly, but we use {@link #getSubscribedIntensity()} {@code * 200} to set this value.</li>
	 * 	<li>Standard mode: PREF field in kVA.</li>
	 * </ul>
	 * @return the reference (subscribed) apparent power, in Volt Ampere (VA).
	 */
	public synchronized Integer getReferencePower() { return referencePower; }
	
	
	
	private Integer cutPower = null;
	private void updateCutPower(String p) {
		Integer oldCutPower = cutPower;
		
		cutPower = Integer.parseInt(p) * 1000;

		if (!Objects.equals(cutPower, oldCutPower))
			MQTTSender.CUT_POWER.publish(cutPower);
	}
	/**
	 * The maximum apparent power allowed before cutting the current.
	 * <p>
	 * May be different than the reference power.
	 * <p>
	 * <ul>
	 * 	<li>Historical mode: not provided directly, but we use {@link #getSubscribedIntensity()} {@code * 200} to set this value.</li>
	 * 	<li>Standard mode: PCOUP field in kVA.</li>
	 * </ul>
	 * @return the maximum allowed apparent power, in Volt Ampere (VA).
	 */
	public synchronized Integer getCutPower() { return cutPower; }
	
	
	private Integer apparentPower = null;
	private void updateApparentPower(String i) {
		Integer oldApparentPower = apparentPower;
		
		apparentPower = Integer.parseInt(i);
		
		if (!Objects.equals(apparentPower, oldApparentPower))
			MQTTSender.APPARENT_POWER.publish(apparentPower);
	}
	/**
	 * The current apparent power.
	 * <p>
	 * <ul>
	 * 	<li>Historical mode: PAPP field, with a precision of 10 VA.</li>
	 * 	<li>Standard mode: SINSTS field, with a precision of 1 VA.</li>
	 * </ul>
	 * @return the current apparent power, in Volt Ampere (VA).
	 */
	public synchronized Integer getApparentPower() { return apparentPower; }
	

	private Integer maxPowerToday = null;
	private Long maxPowerTimeToday = null;
	private void updateMaxPowerToday(long t, String i) {
		maxPowerToday = Integer.parseInt(i);
		maxPowerTimeToday = t;
	}
	/**
	 * The max apparent power of today.
	 * <p>
	 * <ul>
	 * 	<li>Historical mode: not provided.</li>
	 * 	<li>Standard mode: SMAXSN field.</li>
	 * </ul>
	 * @return the max apparent power of today, in Volt Ampere (VA).
	 */
	public synchronized Integer getMaxPowerToday() { return maxPowerToday; }
	/**
	 * The time when the max apparent power of today was registered.
	 * <p>
	 * <ul>
	 * 	<li>Historical mode: not provided.</li>
	 * 	<li>Standard mode: SMAXSN datetime field.</li>
	 * </ul>
	 * @return the millisecond epoch time of when the max apparent power of today was registered.
	 */
	public synchronized Long getMaxPowerTimeToday() { return maxPowerTimeToday; }
	

	private Integer maxPowerYesterday = null;
	private Long maxPowerTimeYesterday = null;
	private void updateMaxPowerYesterday(long t, String i) {
		maxPowerYesterday = Integer.parseInt(i);
		maxPowerTimeYesterday = t;
	}
	/**
	 * The max apparent power of yesterday.
	 * <p>
	 * <ul>
	 * 	<li>Historical mode: not provided.</li>
	 * 	<li>Standard mode: SMAXSN-1 field.</li>
	 * </ul>
	 * @return the max apparent power of yesterday, in Volt Ampere (VA).
	 */
	public synchronized Integer getMaxPowerYesterday() { return maxPowerYesterday; }
	/**
	 * The time when the max apparent power of today was registered.
	 * <p>
	 * <ul>
	 * 	<li>Historical mode: not provided.</li>
	 * 	<li>Standard mode: SMAXSN-1 datetime field.</li>
	 * </ul>
	 * @return the millisecond epoch time of when the max apparent power of today was registered.
	 */
	public synchronized Long getMaxPowerTimeYesterday() { return maxPowerTimeYesterday; }
	
	
	
	
	
	
	
	
	
	
	private int totalIndex = 0;
	private final int[] indexes = new int[10];
	private final String[] indexNames = new String[10];
	private int currentIndex = 0;
	
	private void updateIndexHist(String k, String v) {
		int i = getIndexIdFromHistId(k);
		indexes[i] = Integer.parseInt(v);
		indexNames[i] = k;
		
		int oldTotalIndex = totalIndex;
		
		totalIndex = 0;
		for (int idx : indexes)
			totalIndex += idx;
		
		if (totalIndex != oldTotalIndex)
			MQTTSender.INDEX_TOTAL.publish(totalIndex);
	}
	private void updateCurrIndexHist(String i) {
		currentIndex = getIndexIdFromHistId(i);
	}
	private static int getIndexIdFromHistId(String i) {
		switch (i) {
			case "HCHP": // index name
			case "EJPHPM": // index name
			case "BBRHPJB": // index name
			case "HP..": // PTEC value
			case "PM..": // PTEC value
			case "HPJB": // PTEC value
				return 1;
			case "BBRHCJW": // index name
			case "HCJW": // PTEC value
				return 2;
			case "BBRHPJW": // index name
			case "HPJW": // PTEC value
				return 3;
			case "BBRHCJR": // index name
			case "HCJR": // PTEC value
				return 4;
			case "BBRHPJR": // index name
			case "HPJR": // PTEC value
				return 5;
		}
		return 0; // BASE, HCHC, EJPHN, BBRHCJB ; TH.., HC.., HN.., HCJB
	}
	
	
	
	private void updateIndexStd(String k, String v) { indexes[Integer.parseInt(k)-1] = Integer.parseInt(v); }
	private void updateTotalIndexStd(String v) {
		int oldTotalIndex = totalIndex;
		
		totalIndex = Integer.parseInt(v);
		
		if (totalIndex != oldTotalIndex)
			MQTTSender.INDEX_TOTAL.publish(totalIndex);
	}
	private void updateCurrentIndexId(String v) { currentIndex = Integer.parseInt(v) - 1; }
	private void updateNameOfCurrentIndex(String v) { indexNames[currentIndex] = v; }
	
	
	/**
	 * List of the 10 indexes managed by the electric meter.
	 * <p>
	 * <ul>
	 * 	<li>Historical mode: fields with various names. All the indexes are not transmitted, so others indexes are considered 0.</li>
	 * 	<li>Standard mode: EASFxx fields.</li>
	 * </ul>
	 * @return the full list of indexes, all measured in Watt hour (Wh)
	 */
	public synchronized int[] getIndexes() { return Arrays.copyOf(indexes, indexes.length); }
	/**
	 * List of the names for each indexes.
	 * <p>
	 * <ul>
	 * 	<li>Historical mode: the name of an index is based on the name of the TIC field that provide the index.</li>
	 * 	<li>Standard mode: the name provided by the field LTARF is applied to the current running index specified by NTARF.</li>
	 * </ul>
	 * @return the full list of name for each indexes. Unknown names are null.
	 */
	public synchronized String[] getIndexNames() { return Arrays.copyOf(indexNames, indexNames.length); }
	public synchronized int getIndex(int i) { return indexes[i]; }
	public synchronized String getIndexName(int i) { return indexNames[i]; }
	public synchronized int getIndexCount() { return indexes.length; }
	public synchronized int getTotalIndex() { return totalIndex; }
	public synchronized int getCurrentIndex() { return currentIndex; }
	
	
	

	private final int[] distributorIndexes = new int[4];
	private int currentDistributorIndex = 0;
	private void updateDistributorIndex(String k, String v) { distributorIndexes[Integer.parseInt(k)-1] = Integer.parseInt(v); }
	/**
	 * List of the 4 distributor indexes managed by the electric meter.
	 * <p>
	 * These indexes are not related to the pricing applied for the customer.
	 * <p>
	 * <ul>
	 * 	<li>Historical mode: not provided.</li>
	 * 	<li>Standard mode: EASDxx fields.</li>
	 * </ul>
	 * @return the full list of distributor indexes, all measured in Watt hour (Wh). In historical mode, all values are 0.
	 */
	public synchronized int[] getDistributorIndexes() { return Arrays.copyOf(distributorIndexes, distributorIndexes.length); }
	public synchronized int getDistributorIndex(int i) { return distributorIndexes[i]; }
	public synchronized int getDistributorIndexCount() { return distributorIndexes.length; }
	public synchronized int getCurrentDistributorIndex() { return currentDistributorIndex; }
	
	
	
	
	private Integer rmsCurrent = null;
	private void updateRMSCurrent(String i) { rmsCurrent = Integer.parseInt(i); }
	public synchronized Integer getRMSCurrent() { return rmsCurrent; }
	
	
	
	private Integer rmsVoltage = null;
	private void updateRMSVoltage(String i) { rmsVoltage = Integer.parseInt(i); }
	public synchronized Integer getRMSVoltage() { return rmsVoltage; }
	
	

	private Integer avgVoltage = null;
	private Long avgVoltageTime = null;
	private void updateAvgVoltage(long t, String i) {
		avgVoltage = Integer.parseInt(i);
		avgVoltageTime = t;
	}
	public synchronized Integer getAvgVoltage() { return avgVoltage; }
	public synchronized Long getAvgVoltageLastUpdate() { return avgVoltageTime; }
	
	
	
	private Integer providerCalendarDayToday = null;
	private Integer providerCalendarDayTomorrow = null;
	private void updateProviderCalDayToday(String d) { providerCalendarDayToday = Integer.parseInt(d); }
	private void updateProviderCalDayTomorrow(String d) { providerCalendarDayTomorrow = Integer.parseInt(d); }
	public synchronized Integer getProviderCalendarDayToday() { return providerCalendarDayToday; }
	public synchronized Integer getProviderCalendarDayTomorrow() { return providerCalendarDayTomorrow; }
	
	
	
	
	private String message1 = null, message2 = null;
	private void updateMessage1(String m) { message1 = m.trim(); }
	private void updateMessage2(String m) { message2 = m == null ? null : m.trim(); }
	public synchronized String getMessage1() { return message1; }
	public synchronized String getMessage2() { return message2; }
	
	
	
	private boolean[] relaysClosed = null;
	private void updateRelaysStatus(String r) {
		int reg = Integer.parseInt(r);
		relaysClosed = new boolean[8];
		for (int i = 0; i < 8; i++) {
			relaysClosed[i] = (reg & 1) == 1;
			reg >>= 1;
		}
	}
	/**
	 * The status of the relays.
	 * <p>
	 * The relay id is 0 based (first relay is 0)
	 * <p>
	 * <ul>
	 * 	<li>Historical mode: not provided.</li>
	 * 	<li>Standard mode: RELAIS field.</li>
	 * </ul>
	 * @param r the 0-based relay id (from 0 to 7). The relay 0 is the physical relay in the electric meter.
	 * @return true if the specified relay is closed (the current goes through), false if the TIC says otherwise, or null if TIC does not provide the information.
	 */
	public synchronized Boolean isRelayClosed(int r) {
		if (relaysClosed == null || r < 0 || r >= relaysClosed.length)
			return null;
		return relaysClosed[r];
	}
	/**
	 * The status of the relays.
	 * <p>
	 * <ul>
	 * 	<li>Historical mode: not provided.</li>
	 * 	<li>Standard mode: RELAIS field.</li>
	 * </ul>
	 * @return the status of all the relays. The relay 0 is the physical one. For each relay, true means closed (the current goes through), and false means opened.
	 */
	public synchronized boolean[] getRelaysStatus() {
		return relaysClosed;
	}
	
	
	
	
	private CutRelayStatus cutRelayStatus = null;
	private Boolean providerCoverOpened = null;
	private Boolean overVoltage = null;
	private Boolean overPowerConsumption = null;
	private Boolean producerMode = null;
	private Boolean activeEnergyNegative = null;
	private Boolean internalClockBad = null;
	private EuridisOutputStatus euridisOutputStatus = null;
	private CPLStatus cplStatus = null;
	private Boolean cplSynchronized = null;
	private void updateStatusRegister(String r) {
		int reg = Integer.parseInt(r, 16);
		
		int isOffPeakRelayOpened   = reg & 0x1; reg >>= 1;
		int cutRelayStatusRaw      = reg & 0x7; reg >>= 3;
		int isProviderCoverOpened  = reg & 0x1; reg >>= 1;
		    /* unused bit */                    reg >>= 1;
		int isOverVoltage          = reg & 0x1; reg >>= 1;
		int isOverPowerConsumption = reg & 0x1; reg >>= 1;
		int isProducerMode         = reg & 0x1; reg >>= 1;
		int isActiveEnergyNegative = reg & 0x1; reg >>= 1;
		int currentActiveIndex     = reg & 0xF; reg >>= 4;
		int currentDistIndexRaw    = reg & 0x3; reg >>= 2;
		int isInternalClockBad     = reg & 0x1; reg >>= 1;
		int isTICModeStandard      = reg & 0x1; reg >>= 1;
	        /* unused bit */                    reg >>= 1;
		int euridisOutputStatusRaw = reg & 0x3; reg >>= 2;
		int cplStatusRaw           = reg & 0x3; reg >>= 2;
		int isCPLSynchronized      = reg & 0x1; reg >>= 1;
		int tempoCurrentDayColorRaw= reg & 0x3; reg >>= 2;
		int tempoNextDayColorRaw   = reg & 0x3; reg >>= 2;
		//int mobilePointNotice      = reg & 0x3; reg >>= 2;
		//int currentMobilePoint     = reg & 0x3; reg >>= 2;
	    
		
		if (isRelayClosed(0) != null && ((isOffPeakRelayOpened == 0) != isRelayClosed(0))) {
			Log.warn("Inconsistent status of relay 0 between RELAIS data set and value in STGE data set."
					+ " RELAIS says relay 0 is " + (isRelayClosed(0) ? "closed" : "opened") + " and"
					+ " STGE ’s bit 0 says off peak relay is " + (isOffPeakRelayOpened == 0 ? "closed" : "opened") + ".");
		}
		
		cutRelayStatus = cutRelayStatusRaw < CutRelayStatus.values().length ? CutRelayStatus.values()[cutRelayStatusRaw] : null;
		providerCoverOpened = isProviderCoverOpened == 1;
		overVoltage = isOverVoltage == 1;
		overPowerConsumption = isOverPowerConsumption == 1;
		producerMode = isProducerMode == 1;
		activeEnergyNegative = isActiveEnergyNegative == 1;
		
		if (currentActiveIndex != currentIndex) {
			Log.warn("Inconsistent current index id between NTARF data set value and current active index value in register in STGE data set");
		}
		
		currentDistributorIndex = currentDistIndexRaw;
		internalClockBad = isInternalClockBad == 1;
		
		if (isTICModeStandard != 1) {
			Log.warn("Register in STGE data set declared TIC mode as historical but we are processing the data frame as standard mode. (STGE is only send in standard mode, btw)");
		}
		
		euridisOutputStatus = euridisOutputStatusRaw < EuridisOutputStatus.values().length ? EuridisOutputStatus.values()[euridisOutputStatusRaw] : null;
		cplStatus = cplStatusRaw < CPLStatus.values().length ? CPLStatus.values()[cplStatusRaw] : null;
		cplSynchronized = isCPLSynchronized == 1;
		tempoCurrentDayColor = tempoCurrentDayColorRaw < TempoDayColor.values().length ? TempoDayColor.values()[tempoCurrentDayColorRaw] : null;
		tempoNextDayColor = tempoNextDayColorRaw < TempoDayColor.values().length ? TempoDayColor.values()[tempoNextDayColorRaw] : null;
		
		
	}
	public enum CutRelayStatus {
		CLOSED, OPENED_OVERPOWER, OPENED_OVERVOLTAGE, OPENED_REMOTE_CONTROL,
		OPENED_OVERHEAT_WITH_OVERCURRENT, OPENED_OVERHEAT_NO_OVERCURRENT
	}
	public enum EuridisOutputStatus {
		DISABLED, ENABLED_UNSECURED, UNDEFINED, ENABLED_SECURED
	}
	public enum CPLStatus { NEW_UNLOCK, NEW_LOCK, REGISTERED }
	public synchronized CutRelayStatus getCutRelayStatus() { return cutRelayStatus; }
	public synchronized Boolean isProviderCoverOpened() { return providerCoverOpened; }
	public synchronized Boolean isOverVoltage() { return overVoltage; }
	public synchronized Boolean isOverPowerConsumption() { return overPowerConsumption; }
	public synchronized Boolean isProducerMode() { return producerMode; }
	public synchronized Boolean isActiveEnergyNegative() { return activeEnergyNegative; }
	public synchronized Boolean isInternalClockBad() { return internalClockBad; }
	public synchronized EuridisOutputStatus getEuridisOutputStatus() { return euridisOutputStatus; }
	public synchronized CPLStatus getCPLStatus() { return cplStatus; }
	public synchronized Boolean isCPLSynchronized() { return cplSynchronized; }
	

	
	

	private TempoDayColor tempoCurrentDayColor = null;
	private TempoDayColor tempoNextDayColor = null;
	private void updateDEMAIN(String o) {
		if (o.equals("----"))
			tempoNextDayColor = TempoDayColor.UNKNOWN;
		else if (o.equals("BLEU"))
			tempoNextDayColor = TempoDayColor.BLUE;
		else if (o.equals("BLAN"))
			tempoNextDayColor = TempoDayColor.WHITE;
		else if (o.equals("ROUG"))
			tempoNextDayColor = TempoDayColor.RED;
		else
			tempoNextDayColor = null;
	}
	public enum TempoDayColor { UNKNOWN, BLUE, WHITE, RED }
	public synchronized TempoDayColor getTempoCurrentDayColor() { return tempoCurrentDayColor; }
	public synchronized TempoDayColor getTempoNextDayColor() { return tempoNextDayColor; }
	
	
	
	
	
	private List<DayScheduleTimeSlot> nextDayScheduling = null;
	private void updateNextDayScheduling(String d) {
		String[] rawSlots = d.split(" ");
		nextDayScheduling = new ArrayList<>();
		for (String rawSlot : rawSlots) {
			if (rawSlot.length() != 8 || rawSlot.equals("NONUTILE"))
				continue;
			
			int hour = Integer.parseInt(rawSlot.substring(0, 2));
			int minute = Integer.parseInt(rawSlot.substring(2, 4));
			
			int reg = Integer.parseInt(rawSlot.substring(4), 16);
			
			int indexChangeRaw = reg & 0xF; reg >>= 4;
			boolean[] virtRelays = new boolean[7];
			for (int i = 0; i < 7; i++) {
				virtRelays[i] = (reg & 0x1) == 1;
				reg >>= 1;
			}
			/* unused bits */ reg >>= 3;
			int physRelayChange = reg & 0x3; reg >>= 2;
		
			nextDayScheduling.add(new DayScheduleTimeSlot(
					minute * 60_000L + hour * 3_600_000L,
					PhysicalRelayChange.values()[physRelayChange],
					indexChangeRaw >= 1 && indexChangeRaw <= 10 ? (indexChangeRaw - 1) : null,
					virtRelays));
		}
	}
	public static class DayScheduleTimeSlot {
		public final long millisDayStart;
		public final PhysicalRelayChange physicalRelayChange;
		public final Integer changeToIndex;
		public final boolean[] virtualRelaysStatus;
		
		public DayScheduleTimeSlot(long start, PhysicalRelayChange physRelay, Integer indexChange, boolean[] virtRelays) {
			millisDayStart = start;
			physicalRelayChange = physRelay;
			changeToIndex = indexChange;
			virtualRelaysStatus = virtRelays;
		}
		
		public enum PhysicalRelayChange { NO_CHANGE, TEMPO_OR_NO_CHANGE, OPEN, CLOSE }
	}
	public synchronized List<DayScheduleTimeSlot> getNextDayScheduling() { return nextDayScheduling; }
	
	
	
	
	
	
	
	private String MOTDETAT = null;
	private void updateMOTDETAT(String o) { MOTDETAT = o; }
	/**
	 * The value of MOTDETAT from the historical TIC data frame.
	 * <p>
	 * The interpretation of the data is not documented.
	 * <p>
	 * <ul>
	 * 	<li>Historical mode: MOTDETAT field.</li>
	 * 	<li>Standard mode: not provided.</li>
	 * </ul>
	 * @return the raw value of MOTDETAT, or null if absent.
	 * @deprecated this value is only supported on historical TIC mode, and its interpretation is not documented.
	 */
	@Deprecated
	public synchronized String getMOTDETAT() { return MOTDETAT; }
	
	
	
	private String HHPHC = null;
	private void updateHHPHC(String o) { HHPHC = o; }
	/**
	 * The value of HHPHC from the historical TIC data frame, indicating the time in the day for the .
	 * <p>
	 * The interpretation of the data is not documented.
	 * <p>
	 * <ul>
	 * 	<li>Historical mode: HHPHC field.</li>
	 * 	<li>Standard mode: not provided.</li>
	 * </ul>
	 * @return the raw value of HHPHC, or null if absent.
	 * @deprecated this value is only supported on historical TIC mode, and its interpretation is not documented.
	 */
	@Deprecated
	public synchronized String getHHPHC() { return HHPHC; }
	
	
	
	
	private boolean histMobilePointNotice = false;
	/**
	 * The notice for the upcoming mobile point (EJP subscription).
	 * <p>
	 * <ul>
	 * 	<li>Historical mode: presence of PEJP field, during 30 minutes before the start of the mobile point.</li>
	 * 	<li>Standard mode: not provided.</li>
	 * </ul>
	 * @return true if the TIC is in historical mode and a mobile point will start in the next 30 minutes, false otherwise.
	 * @deprecated this value is only supported on historical TIC mode.
	 */
	@Deprecated
	public synchronized boolean getHistMobilePointNotice() { return histMobilePointNotice; }
	
	
	
	
	
	
	
	
}
