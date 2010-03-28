package org.cat;

import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;

/**
 * Short description.
 * <p/>
 * Detail description.
 * @author <a href="mailto:striped@gmail.com">Val</a>
 * @version $Revision: 1.0 $ $Date: Mar 28, 2010 6:00:04 PM $
 * @created Mar 28, 2010 6:00:04 PM by striped
 * @todo add JavaDoc
 */
public class Performer implements Runnable {
    protected int width;
    protected int from;
    protected int to;
    protected long address;
    protected long pointer;
    protected CyclicBarrier barrier;

    public void init(final DirectImageBuilder builder, final int from, final int to) {
        this.width = builder.width;
        this.from = from;
        this.to = to;
        this.address = builder.address;
        reset();
    }

    public void reset() {
        this.pointer = address + (this.to * width << 2);
    }

    public void run() {
        if (null != barrier) {
            try {
                barrier.await();
            } catch (InterruptedException e) {
                // ignore
            } catch (BrokenBarrierException e) {
                // ignore
            }
        }
    }

    public void setBarrier(final CyclicBarrier barrier) {
        this.barrier = barrier;
    }
}
