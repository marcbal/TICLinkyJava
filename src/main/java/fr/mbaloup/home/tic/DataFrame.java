package fr.mbaloup.home.tic;

import java.util.Collections;
import java.util.Map;

public class DataFrame {
	public final long time;
	public final Map<String, DataSet> data;
	public DataFrame(long t, Map<String, DataSet> ig) {
		time = t;
		data = Collections.unmodifiableMap(ig);
	}
	@Override
	public String toString() {
		return "{time=" + time + ",infoGroups=" + data + "}";
	}
	public void ifKeyPresent(String k, DataSetExecutor run) {
		if (data.containsKey(k))
			run.consume(k, data.get(k), time);
	}
	
	public interface DataSetExecutor {
		void consume(String k, DataSet ds, long dfTime);
	}
}
