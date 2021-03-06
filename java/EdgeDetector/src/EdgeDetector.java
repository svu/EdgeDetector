import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;

import javax.imageio.ImageIO;

public class EdgeDetector {
	private long start;
	private long last;

	private BufferedImage image;
	private int newPixels[];
	private int oldPixels[];
	private int width;
	private int height;

	private final String inName;
	private final String outName;
	private final CyclicBarrier barrier;

	public final int[] getNewPixels() {
		return newPixels;
	}

	public final int[] getOldPixels() {
		return oldPixels;
	}

	public final CyclicBarrier getBarrier() {
		return barrier;
	}

	public EdgeDetector(final String inName, final String outName, final int num) {
		this.inName = inName;
		this.outName = outName;
		this.barrier = new CyclicBarrier(num + 1);
	}

	public void reportProgress(String action) {
		final long now = System.nanoTime();
		System.out.println(action + ", " + (now - start) + ", " + (now - last));
		last = now;
	}

	public void load(String filename) throws IOException {
		image = ImageIO.read(new File(filename));
		width = image.getWidth();
		height = image.getHeight();
		oldPixels = new int[width * height];
		newPixels = new int[width * height];

		image.getRGB(0, 0, width, height, oldPixels, 0, width);
		reportProgress("Loaded");
	}

	public void save(String filename) throws IOException {
		final BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		image.setRGB(0, 0, width, height, newPixels, 0, width);
		ImageIO.write(image, "PNG", new File(filename));
		reportProgress("Written");
	}

	public void detect() throws IOException {
		last = start = System.nanoTime();
		System.out.println("Started");

		load(inName);
		process();
		save(outName);
	}

	public void process() {
		int parts = barrier.getParties() - 1;
		int step = height / parts;
		int high = Processor.BORDER_WIDTH, low = step;
		for (int i = 0; i < parts; i++) {
			if (low > (height - Processor.BORDER_WIDTH))
				low = height - Processor.BORDER_WIDTH;
			final Processor p = new Processor(this, image, high, low);
			new Thread(p).start();

			high = low;
			low += step;
		}

		try {
			barrier.await();
		} catch (InterruptedException ex) {
		} catch (BrokenBarrierException ex) {
		}

		reportProgress("Processed");
	}

	public static void main(String args[]) {

		try {
			if (args.length != 4) {
				System.err.println("Usage: java EdgeDetector {basedir} {times} {threads} {seq}");
				System.exit(1);
			}

			final String baseDir = args[0];
			final int times = Integer.parseInt(args[1]);
			final int numThreads = Integer.parseInt(args[2]);
			final String sequence = args[3];

			for (Character c : sequence.toCharArray()) {
				final String infile = baseDir + File.separator + "test" + c + ".png";
				final String outfile = baseDir + File.separator + "out" + c + ".png";
				System.out.println("Converting from " + infile + " to " + outfile + ", repeat " + times + " times, using " + numThreads + " threads");
				final EdgeDetector ed = new EdgeDetector(infile, outfile, numThreads);

				for (int i = 0; i < times; i++) {
					System.out.println("Iteration " + i);
					ed.detect();
				}
			}
		} catch (IOException ex) {
			System.err.println(ex);
			ex.printStackTrace();
		}
	}

}
