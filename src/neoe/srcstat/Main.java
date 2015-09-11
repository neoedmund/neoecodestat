package neoe.srcstat;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.List;

import neoe.util.Config;
import neoe.util.FileIterator;

public class Main {

	public static void main(String[] args) throws Exception {
		Config.CONF_FN = "conf.py";
		new Main().run();
	}

	private void run() throws Exception {
		List ext = (List) Config.get("ext");
		List dir = (List) Config.get("dir");
		List ignore = (List) Config.get("ignore");
		System.out.println(ext);
		System.out.println(dir);
		for (Object o : dir) {
			stat((String) o, ext, ignore);
		}

	}

	private void stat(String dir, List ext, List ignore) throws IOException {
		// init
		int extsize = ext.size();
		System.out.println("stat dir=" + dir);
		int[][] data;
		data = new int[extsize + 1][];

		for (int i = 0; i < extsize + 1; i++) {
			data[i] = new int[3]; // file cnt, line cnt, bytes
		}

		// count files
		FileIterator fi = new FileIterator(dir);
		for (File f : fi) {
			if (f.isDirectory())
				continue;
			if (isIgnored(f, ignore))
				continue;
			String name = f.getName().toLowerCase();
			int i = 0;
			boolean found = false;
			for (Object o : ext) {
				String ext1 = ("." + o).toLowerCase();
				if (name.endsWith(ext1)) {
					statFile(data[i], f, true);
					found = true;
				}
				i++;
			}
			if (!found) {
				statFile(data[i], f, false);
			}
		}

		// print result
		for (int i = 0; i < extsize; i++) {
			int[] v = data[i];
			System.out.printf("%s\t%,3d files\t%,3d lines\t%,3d bytes\n", ext.get(i), v[0], v[1], v[2]);
		}
		{
			int[] v = data[extsize];
			System.out.printf("%s\t%,3d files\t%,3d lines\t%,3d bytes\n", "others", v[0], v[1], v[2]);
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

	private void statFile(int[] v, File f, boolean line) throws IOException {
		v[0]++;
		if (line)
			v[1] += getLines(f);
		v[2] += getSize(f);
	}

	private int getSize(File f) {
		return (int) f.length();
	}

	private int getLines(File f) throws IOException {
		int cnt = 0;
		BufferedReader in = new BufferedReader(new FileReader(f));
		String line;
		while ((line = in.readLine()) != null) {
			cnt++;
		}
		return cnt;
	}

}
