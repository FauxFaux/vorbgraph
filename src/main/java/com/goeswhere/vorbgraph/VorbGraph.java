package com.goeswhere.vorbgraph;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Date;
import java.util.Iterator;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.commons.io.FileUtils;

public class VorbGraph {

	static class Result {
		double diff;
		File f;
	}

	public static void main(String[] args) throws IOException, InterruptedException, ExecutionException {
		if (args.length != 2) {
			System.out.println("usage: fileordir window(seconds)");
			return;
		}

		ExecutorService e = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors() * 2);
		CompletionService<Result> ecs = new ExecutorCompletionService<Result>(e);

		final double window = Double.parseDouble(args[1]);

		final String s = args[0];
		final File f = new File(s);
		if (f.isDirectory()) {
			System.err.print("Building list..");
			int sub = 0;
			for (final File j : new Iterable<File>() {
				@SuppressWarnings("unchecked")
				@Override public Iterator<File> iterator() {
					return FileUtils.iterateFiles(f, new String[] { "ogg" }, true);
				}
			}) {
				ecs.submit(new Callable<Result>() {
					@Override public Result call() throws Exception {
						Result ret = new Result();
						ret.f = j;
						try {
							ret.diff = computeDiff(j, window, false);
						} catch (RuntimeException x) {
							System.err.println("unacceptable file: " + j);
							x.printStackTrace(System.err);
						}
						return ret;
					}
				});
				++sub;
			}
			System.err.println(".  Done.");
			e.shutdown();
			double maxdiff = 0;
			File bestfile = null;
			long laststat = t();
			while (sub-- != 0) {
				Result r = ecs.take().get();
				final long t = t();

				if (r.diff > maxdiff) {
					maxdiff = r.diff;
					bestfile = r.f;
					System.err.println("New winner, " + sub + " to go: " + bestfile + ": " + maxdiff);
					laststat = t;
				} else if (t - laststat > 2500) {
					System.err.println(sub + " left to process");
					laststat = t;
				}
			}
			System.err.println("Finished.  Victor was " + bestfile + ": " + maxdiff);
			System.out.println();
			computeDiff(bestfile, window, true);
		} else
			computeDiff(f, window, true);
	}

	public static long t() {
		return new Date().getTime();
	}

	private static double computeDiff(File f, final double window, boolean print) throws IOException {
		final InputStream fi = new FileInputStream(f);
		final byte[] header = new byte[27];
		int bytesread = 0;
		double ltp = 0;
		int lb = 0;
		double min = Double.MAX_VALUE;
		double max = 0;
		int samples = 0;
		double total = 0;
		while (header.length == fi.read(header)) {
			if ('O' != header[0] || 'g' != header[1] || 'g' != header[2] || 'S' != header[3])
				throw new RuntimeException("Bad header");
			int a = b2i(header[26]);

			bytesread += 27 + a;

			final byte[] segTable = new byte[a];
			fi.read(segTable);
			int skip = 0;
			for (byte b : segTable)
				skip += b2i(b);

			bytesread += skip;

			if (skip != fi.skip(skip))
				throw new RuntimeException("Bad skip");

			double timepassed = intify(header, 6) / 44100.0;
			final double period = timepassed - ltp;
			if (period > window) {
				final double byperper = (bytesread - lb) / period;
				if (byperper > max)
					max = byperper;
				if (byperper < min)
					min = byperper;
				if (print)
					System.out.println(ltp + "\t" + byperper);
				total += byperper;
				++samples;
				ltp = timepassed;
				lb = bytesread;
			}
		}
		final double avg = total / samples;
		return (max - avg) / avg;
	}

	private static int b2i(byte b) {
		return b < 0 ? 256 + b : b;
	}

	private static long intify(byte[] c, int off) {
		long ret = 0;
		for (int i = 0; i < 8; ++i)
			ret += (long) b2i(c[i + off]) << (8 * i);

		return ret;
	}
}
