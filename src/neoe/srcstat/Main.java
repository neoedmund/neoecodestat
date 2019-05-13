package neoe.srcstat;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import neoe.util.Config;
import neoe.util.FileIterator;

public class Main {

	public static void main(String[] args) throws Exception {
		Config.CONF_FN = "conf.py";
		new Main().run();
	}

	private Map<String, Long> etcMap;
	private HashMap<String, long[][]> deepMap;
	private HashMap<String, long[][]> deepSumMap;

	private void run() throws Exception {

		List ext = (List) Config.get("ext");
		List dir = (List) Config.get("dir");
		List ignore = (List) Config.get("ignore");
		String s = "" + Config.get("deep");
		boolean deep = "1".equals(s) || "true".equalsIgnoreCase(s);
		System.out.println(ext);
		System.out.println(dir);
		for (Object o : dir) {
			stat((String) o, ext, ignore, deep);
		}

	}

	private void stat(String dir, List ext, List ignore, boolean deep) throws IOException {
		String saveDir = dir;
		try {
			File fout = new File(saveDir, "neoesrcstat.txt");
			PrintWriter out = new PrintWriter(new BufferedWriter(new OutputStreamWriter(new FileOutputStream(fout))));
			out.println("result of neoesourcecodestat v1.1");
			out.close();
		} catch (Exception ex) {
			System.out.println("stat dir seems cannot write, trying current working dir");
			saveDir = ".";
			File fout = new File(saveDir, "neoesrcstat.txt");
			PrintWriter out = new PrintWriter(new BufferedWriter(new OutputStreamWriter(new FileOutputStream(fout))));
			out.println("result of neoesourcecodestat v1.1");
			out.close();
			System.out.println("ok");
		}
		// init
		etcMap = new HashMap<String, Long>();
		int extsize = ext.size();
		System.out.println("stat dir=" + dir);
		long[][] data;
		int datasize = extsize + 1; // +1: etc
		data = new long[datasize][];
		// file cnt, line cnt, bytes
		int cl = 3;
		long[] add = new long[cl];
		for (int i = 0; i < datasize; i++) {
			data[i] = new long[cl];
		}
		deepMap = new HashMap<String, long[][]>();

		// count files
		FileIterator fi = new FileIterator(dir);

		for (File f : fi) {
			if (f.isDirectory())
				continue;
			if (isIgnored(f, ignore))
				continue;
			String name = f.getName().toLowerCase();
			System.out.println(f.getCanonicalPath());
			int i = 0;
			boolean found = false;
			String deepKey = f.getParentFile().getCanonicalPath();

			for (Object o : ext) {
				String ext1 = ((String) o).toLowerCase();
				if (name.endsWith(ext1)) {
					statFile(add, f, true);
					add(add, data[i]);
					found = true;
					addDeep(deepMap, deepKey, i, add, datasize);
					break;
				}
				i++;
			}
			i = extsize;
			if (!found) {
				statFile(add, f, false); // i=etcIndex
				add(add, data[i]);
				statEtcFiles(f);
				addDeep(deepMap, deepKey, i, add, datasize);
			}
		}

		deepSumMap = new HashMap<String, long[][]>();
		String rootKey = new File(dir).getCanonicalPath();
		for (String key : deepMap.keySet()) {
			if (key.equals(rootKey))
				continue;
			long[][] values = deepMap.get(key);
			File f = new File(key);
			String fn = f.getCanonicalPath();
			while (true) {
				addDeep(deepSumMap, fn, values);
				if (fn.equals(rootKey) || fn.length() <= rootKey.length()) {
					break;
				}
				f = f.getParentFile();
				fn = f.getCanonicalPath();
			}
		}

		File fout = new File(saveDir, "neoesrcstat.txt");
		PrintWriter out = new PrintWriter(new BufferedWriter(new OutputStreamWriter(new FileOutputStream(fout))));
		out.println("result of neoesourcecodestat v1.1");
		out.println("dir:" + dir);
		// print result
		for (int i = 0; i < extsize; i++) {
			long[] v = data[i];
			out.printf("%s\t%,3d files\t%,3d lines\t%,3d bytes\n", ext.get(i), v[0], v[1], v[2]);
		}
		{
			long[] v = data[extsize];
			out.printf("%s\t%,3d files\t%,3d lines\t%,3d bytes\n", "others", v[0], v[1], v[2]);
		}
		out.println("--- others:");

		// etc
		List<Entry<String, Long>> etc = new ArrayList<Entry<String, Long>>(etcMap.entrySet());
		etc.sort(new Comparator<Entry<String, Long>>() {

			@Override
			public int compare(Entry<String, Long> o1, Entry<String, Long> o2) {
				return (int) (-o1.getValue() + o2.getValue());
			}
		});
		long lastCnt = -1;
		for (Entry<String, Long> e : etc) {
			long cnt = e.getValue();
			if (cnt == lastCnt) {
				out.print(", " + e.getKey());
			} else {
				out.printf("\n%,3d : %s", e.getValue(), e.getKey());
			}
			lastCnt = cnt;
		}
		out.printf("\n--- deep (for each of %,3d dir not including subdirs):\n", deepMap.size());
		printDeep(out, deepMap, ext);
		out.printf("--- deepSum (for each of %,3d dir including subdirs):\n", deepSumMap.size());
		printDeep(out, deepSumMap, ext);
		out.close();
		System.out.println("write to " + fout.getAbsolutePath());
	}

	private void add(long[] add, long[] target) {
		int cl = add.length;
		for (int i = 0; i < cl; i++) {
			target[i] += add[i];
		}
	}

	private void printDeep(PrintWriter out, final HashMap<String, long[][]> map, List ext) {
		List<String> keys = new ArrayList<String>(map.keySet());

		out.println("--- sort by sum lines:");
		Collections.sort(keys, new Comparator() {

			@Override
			public int compare(Object o1, Object o2) {
				long[][] d1 = (long[][]) map.get(o1);
				long[][] d2 = (long[][]) map.get(o2);
				long[] r1 = d1[d1.length - 1];
				long[] r2 = d2[d2.length - 1];
				// file cnt, line cnt, bytes
				long v = (-r1[1] + r2[1]);
				if (v < 0)
					v = -1;
				else if (v > 0)
					v = 1;
				return (int) v;
			}
		});
		printDeepByKeys(keys, out, map, ext);

		out.println("--- sort by sum bytes:");
		Collections.sort(keys, new Comparator() {

			@Override
			public int compare(Object o1, Object o2) {
				long[][] d1 = (long[][]) map.get(o1);
				long[][] d2 = (long[][]) map.get(o2);
				long[] r1 = d1[d1.length - 1];
				long[] r2 = d2[d2.length - 1];
				// file cnt, line cnt, bytes
				long v = (-r1[2] + r2[2]);
				if (v < 0)
					v = -1;
				else if (v > 0)
					v = 1;
				return (int) v;
			}
		});
		printDeepByKeys(keys, out, map, ext);

		out.println("--- sort by name:");
		Collections.sort(keys);
		printDeepByKeys(keys, out, map, ext);
	}

	private void printDeepByKeys(List<String> keys, PrintWriter out, HashMap<String, long[][]> map, List ext) {
		int total = keys.size();
		int index = 0;
		for (String key : keys) {
			long[][] rows = map.get(key);

			long bytes = rows[rows.length - 1][2];

			out.printf("%s\t(%d/%d) %,3d bytes\n", key, ++index, total, bytes);

			for (int i = 0; i < rows.length - 2; i++) {
				long[] row = rows[i];
				if (row[0] > 0) {
					out.printf("\t%s\t%,3d files\t%,3d lines\t%,3d bytes\n", ext.get(i), row[0], row[1], row[2]);
				}
			}
			{
				long[] row = rows[rows.length - 2];
				if (row[0] > 0) {
					out.printf("\t%s\t%,3d files\t%,3d lines\t%,3d bytes\n", "[others]", row[0], row[1], row[2]);
				}
			}
			{
				long[] row = rows[rows.length - 1];
				if (row[0] > 0) {
					out.printf("\t%s\t%,3d files\t%,3d lines\t%,3d bytes\n", "[sum]", row[0], row[1], row[2]);
				}
			}
		}
	}

	/**
	 * add deepmap data to deepsummap.
	 * 
	 * @param map
	 * @param key
	 * @param data
	 */
	private void addDeep(HashMap<String, long[][]> map, String key, long[][] data) {
		long[][] values = map.get(key);
		int cl = data[0].length;
		int datasize = data.length;
		if (values == null) {
			values = new long[datasize][];
			for (int i = 0; i < values.length; i++) {
				values[i] = new long[cl];
			}
			map.put(key, values);
		}
		for (int i = 0; i < datasize; i++) {
			for (int j = 0; j < cl; j++) {
				values[i][j] += data[i][j];
			}
		}
	}

	private void addDeep(HashMap<String, long[][]> map, String key, int x, long[] data, int datasize) {
		long[][] values = map.get(key);
		int cl = data.length;
		if (values == null) {
			values = new long[datasize + 1][]; // +1 sum lines, sum size
			for (int i = 0; i < values.length; i++) {
				values[i] = new long[cl];
			}
			map.put(key, values);
		}
		long[] cell = values[x];
		long[] cellLast = values[datasize];
		for (int i = 0; i < cl; i++) {
			cell[i] += data[i];
			cellLast[i] += data[i];
		}
	}

	private void statEtcFiles(File f) {

		String fn = f.getName();
		int p1 = fn.lastIndexOf('.');
		String ext = "";
		if (p1 >= 0) {
			ext = fn.substring(p1);
		} else {
			ext = fn;
		}
		// add map
		Long cnt = etcMap.get(ext);
		if (cnt == null) {
			etcMap.put(ext, 1L);
		} else {
			etcMap.put(ext, cnt + 1);
		}

	}

	private boolean isIgnored(File f, List ignore) {
		for (Object o : ignore) {
			String s = o.toString().toLowerCase();
			String path = "/" + f.getAbsolutePath().replace('\\', '/') + "/";
			path = path.toLowerCase();
			if (path.indexOf(s) >= 0)
				return true;
		}
		return false;
	}

	private void statFile(long[] v, File f, boolean line) throws IOException {
		v[0] = 1;
		if (line)
			v[1] = getLines(f);
		else
			v[1] = 0;
		v[2] = getSize(f);
	}

	private int getSize(File f) {
		return (int) f.length();
	}

	private int getLines(File f) throws IOException {
		try {
			int cnt = 0;
			BufferedReader in = new BufferedReader(new FileReader(f));
			String line;
			while ((line = in.readLine()) != null) {
				cnt++;
			}
			return cnt;
		} catch (IOException ex) {
			System.err.println(ex);
			return 0;
		}
	}

}
