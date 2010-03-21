import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;

import javax.imageio.ImageIO;

public class EdgeDetector {
	private final static int BORDER_WIDTH = 1;
	private final static int NCOLORS = 3; // R G B

	private final int H[] = { -1, 0, 1, -2, 0, 2, -1, 0, 1 };
	private final int V[] = { -1, -2, -1, 0, 0, 0, 1, 2, 1 };

	private final long start;
	private long last;

	private int newPixels[];

	private int partsToDo;

	private final static int saturate(int val) {
		return (val < 0) ? 0 : (val >= 0x100) ? 0xFF : val;
	}

	public class Processor implements Runnable {
		private int high, low;
		private BufferedImage image;

		public Processor(BufferedImage image, int high, int low) {
			this.image = image;
			this.high = high;
			this.low = low;
		}

		public void run() {
			final int width = image.getWidth();
			int newPixelsOffset = BORDER_WIDTH + (width * high);

			// [0]:B [1]:G [2]:R
			final int colorValuesH[] = new int[NCOLORS];
			final int colorValuesV[] = new int[NCOLORS];

			final int sampleWidth = 1 + (BORDER_WIDTH << 1);
			final int sampleSize = sampleWidth * sampleWidth;
			final int samplePixels[] = new int[sampleSize];

			for (int y = high; y < low; y++, newPixelsOffset += (BORDER_WIDTH << 1)) {
				for (int x = BORDER_WIDTH; x < width - BORDER_WIDTH; x++) {
					// TYPE_INT_ARGB
					image.getRGB(x - BORDER_WIDTH, y - BORDER_WIDTH, sampleWidth, sampleWidth, samplePixels, 0, sampleWidth);

					Arrays.fill(colorValuesH, 0);
					Arrays.fill(colorValuesV, 0);
					for (int o = 0; o < sampleSize; o++) {
						int pixel = samplePixels[o];
						// Process bytes in B, G, R order
						for (int c = 0; c < NCOLORS; c++) {
							final int val = pixel & 0xFF;
							colorValuesH[c] += val * H[o];
							colorValuesV[c] += val * V[o];
							pixel >>= 8;
						}
					}
					int pixel = 0;
					// R, then G, then B
					for (int c = NCOLORS; --c >= 0;) {
						final int h = saturate(colorValuesH[c]);
						final int v = saturate(colorValuesV[c]);
						pixel <<= 8;
						pixel |= (h > v) ? h : v;
					}
					newPixels[newPixelsOffset++] = pixel;
				}
			}

			synchronized (image) {
				partsToDo--;
				image.notifyAll();
			}
		}
	}

	public EdgeDetector() {
		last = start = System.currentTimeMillis();
		System.out.println("Started");
	}

	public void reportProgress(String action) {
		final long now = System.currentTimeMillis();
		System.out.println(action + ", " + (now - start) + ", " + (now - last));
		last = now;
	}

	public BufferedImage load(String filename) throws IOException {
		final BufferedImage rv = ImageIO.read(new File(filename));
		reportProgress("Loaded");
		return rv;
	}

	public void save(BufferedImage newImage, String filename) throws IOException {
		ImageIO.write(newImage, "PNG", new File(filename));
		reportProgress("Written");
	}

	public BufferedImage process(BufferedImage image, int parts) {
		final int width = image.getWidth();
		final int height = image.getHeight();
		newPixels = new int[width * height];

		partsToDo = parts;
		int step = height / parts;
		int high = BORDER_WIDTH, low = step;
		for (int i = 0; i < parts; i++) {
			if (low > (height - BORDER_WIDTH))
				low = height - BORDER_WIDTH;
			final Processor p = new Processor(image, high, low);
			new Thread(p).start();

			high = low;
			low += step;
		}

		try {
			synchronized (image) {
				while (partsToDo > 0) {
					image.wait();
				}
			}
		} catch (InterruptedException ex) {
		}

		final BufferedImage newImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		newImage.setRGB(0, 0, width, height, newPixels, 0, width);
		reportProgress("Processed");
		return newImage;
	}

	public static void main(String args[]) {

		try {
			final String infile = args[0];
			final String outfile = args[1];
			final int times = Integer.parseInt(args[2]);
			final int numThreads = Integer.parseInt(args[3]);

			System.out.println("Converting from " + infile + " to " + outfile + ", repeat " + times + " times, using " + numThreads + " threads");
			for (int i = 0; i < times; i++) {
				final EdgeDetector ed = new EdgeDetector();
				final BufferedImage origImg = ed.load(infile);
				final BufferedImage newImg = ed.process(origImg, numThreads);
				ed.save(newImg, outfile);
			}
		} catch (IOException ex) {
			System.err.println(ex);
			ex.printStackTrace();
		}
	}

}
