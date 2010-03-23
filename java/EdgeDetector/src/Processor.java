import java.awt.image.BufferedImage;
import java.util.Arrays;

public class Processor implements Runnable {
	public final static int BORDER_WIDTH = 1;
	private final static int NCOLORS = 3; // R G B

	private final int H[] = { -1, 0, 1, -2, 0, 2, -1, 0, 1 };
	private final int V[] = { -1, -2, -1, 0, 0, 0, 1, 2, 1 };

	private int high, low;
	private BufferedImage image;
	private EdgeDetector ed;

	public Processor(EdgeDetector ed, BufferedImage image, int high, int low) {
		this.image = image;
		this.high = high - BORDER_WIDTH;
		this.low = low - BORDER_WIDTH;
		this.ed = ed;
	}

	private static int saturate(int val) {
		val = Math.abs(val);
		return (val >= 0x100) ? 0xFF : val;
	}

	public void run() {
		int[] newPixels = ed.getNewPixels();
		final int width = image.getWidth();
		int newPixelsOffset = BORDER_WIDTH + (width * (high + BORDER_WIDTH));

		// [2]:B [1]:G [0]:R
		final int colorValuesH[] = new int[NCOLORS];
		final int colorValuesV[] = new int[NCOLORS];

		final int doubleBorder = BORDER_WIDTH << 1;
		final int sampleWidth = 1 + doubleBorder;
		final int xLimit = width - doubleBorder;

		final int fullHeight = low - high + doubleBorder;
		final int allPixels[] = new int[width * fullHeight];
		// TYPE_INT_ARGB
		image.getRGB(0, high, width, fullHeight, allPixels, 0, width);

		int baseSrcPos = 0;
		for (int iy = low - high; --iy >= 0; newPixelsOffset += doubleBorder, baseSrcPos += doubleBorder) {
			for (int ix = xLimit; --ix >= 0; baseSrcPos++) {

				Arrays.fill(colorValuesH, 0);
				Arrays.fill(colorValuesV, 0);

				int dstPos = 0;
				int srcPos = baseSrcPos;
				int o = 0;
				for (int iiy = sampleWidth; --iiy >= 0; srcPos += width, dstPos += sampleWidth) {
					int curPos = srcPos;
					for (int iix = sampleWidth; --iix >= 0; curPos++, o++) {
						int pixel = allPixels[curPos];
						final int h = H[o];
						final int v = V[o];
						// Process bytes in B, G, R order
						for (int c = NCOLORS; --c >= 0;) {
							final int val = pixel & 0xFF;
							colorValuesH[c] += val * h;
							colorValuesV[c] += val * v;
							pixel >>= 8;
						}
					}
				}

				int pixel = 0;
				// R, then G, then B
				for (int c = 0; c < NCOLORS; c++) {
					final int h = saturate(colorValuesH[c]);
					final int v = saturate(colorValuesV[c]);
					pixel <<= 8;
					pixel |= (h > v) ? h : v;
				}
				newPixels[newPixelsOffset++] = pixel;
			}
		}

		try {
			ed.getBarrier().await();
		} catch (Exception ex) {
		}
	}
}
