package org.myprofiler4j.java.nio;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.myprofiler4j.java.config.ProfConfig;

/**
 * Specialization of the NioSocketHandler class which uses a thread pool to service
 * channels.
 * 
 * @author nhuang
 */
public class PooledNioSocketHandler extends NioSocketHandler {
  private final static Logger logger = Logger
      .getLogger(PooledNioSocketHandler.class.getName());
  private int MAX_THREADS = ProfConfig.instance.maxClientSessions;
  private ThreadPool pool = new ThreadPool(MAX_THREADS);

  public PooledNioSocketHandler(int port) {
    super(port);
  }

  // -------------------------------------------------------------
  public static void main(String[] argv) throws Exception {
    new PooledNioSocketHandler(15599).run();
  }

  // -------------------------------------------------------------
  /**
   * Sample data handler method for a channel with data ready to read. This
   * method is invoked from the go( ) method in the parent class. This handler
   * delegates to a worker thread in a thread pool to service the channel, then
   * returns immediately.
   * 
   * @param key
   *          A SelectionKey object representing a channel determined by the
   *          selector to be ready for reading. If the channel returns an EOF
   *          condition, it is closed here, which automatically invalidates the
   *          associated key. The selector will then de-register the channel on
   *          the next select call.
   */
  protected void readDataFromSocket(SelectionKey key) throws Exception {
    WorkerThread worker = pool.getWorker();
    if (worker == null) {
      // No threads available. Do nothing. The selection
      // loop will keep calling this method until a
      // thread becomes available. This design could
      // be improved.
      logger.warning("No thread available now.");
      return;
    }
    // Invoking this wakes up the worker thread, then returns
    worker.serviceChannel(key);
  }

  protected void reply(SelectionKey key) throws IOException {

  }

  // ---------------------------------------------------------------
  /**
   * A very simple thread pool class. The pool size is set at construction time
   * and remains fixed. Threads are cycled through a FIFO idle queue.
   */
  private class ThreadPool {
    List<WorkerThread> idle = new LinkedList<WorkerThread>();
    volatile short waitorCounter = 0;
    boolean isShutdown = false;

    ThreadPool(int poolSize) {
      // Fill up the pool with worker threads
      for (int i = 0; i < poolSize; i++) {
        WorkerThread thread = new WorkerThread(this);
        // Set thread name for debugging. Start it.
        thread.setName("Profiler-Worker" + (i + 1));
        thread.setDaemon(true);
        thread.start();
        idle.add(thread);
      }
    }

    /**
     * Find an idle worker thread, if any. Could return null.
     */
    WorkerThread getWorker() {
      WorkerThread worker = null;
      synchronized (idle) {
        if (idle.size() > 0) {
          worker = (WorkerThread) idle.remove(0);
        } else {
          waitorCounter++;
          try {
            idle.wait(3000);
            worker = (WorkerThread) idle.remove(0);
          } catch (Exception e) {
            // ignored
          } finally {
            waitorCounter--;
          }
        }
      }
      return (worker);
    }

    /**
     * Called by the worker thread to return itself to the idle pool.
     */
    void returnWorker(WorkerThread worker) {
      synchronized (idle) {
        if (isShutdown) {
          worker.interrupt();
          return;
        }
        idle.add(worker);
        if (waitorCounter > 0) {
          idle.notifyAll();
        }
      }
    }

    void shutdown() {
      this.isShutdown = true;
      synchronized (idle) {
        Iterator<WorkerThread> iter = idle.iterator();
        while (iter.hasNext()) {
          iter.next().interrupt();
          iter.remove();
        }
      }
    }
  }

  /**
   * A worker thread class which can drain channels and echo-back the input.
   * Each instance is constructed with a reference to the owning thread pool
   * object. When started, the thread loops forever waiting to be awakened to
   * service the channel associated with a SelectionKey object. The worker is
   * tasked by calling its serviceChannel( ) method with a SelectionKey object.
   * The serviceChannel( ) method stores the key reference in the thread object
   * then calls notify( ) to wake it up. When the channel has been drained, the
   * worker thread returns itself to its parent pool.
   */
  private class WorkerThread extends Thread {
    // private ByteBuffer buffer = ByteBuffer.allocate(1024 * 8);
    private ThreadPool pool;
    private SelectionKey key;

    WorkerThread(ThreadPool pool) {
      this.pool = pool;
    }

    // Loop forever waiting for work to do
    public synchronized void run() {
      // System.out.println(this.getName() + " is ready");
      while (!Thread.interrupted()) {
        try {
          // Sleep and release object lock
          this.wait();
        } catch (InterruptedException e) {
          break;
        }

        // System.out.println(this.getName() + " has been awakened");
        try {
          if (key == null) {
            continue; // just in case
          }
          drainChannel(key);
        } catch (Exception e) {
          logger.log(Level.SEVERE, "Caught error: ", e);
          // Close channel and wake up selector
          try {
            key.channel().close();
          } catch (IOException ex) {
          }
          key.selector().wakeup();
        } finally {
          if (key.isValid()) {
            // Resume interest in OP_READ
            key.interestOps(key.interestOps() | SelectionKey.OP_READ);
            // Cycle the selector so this key is active again
            key.selector().wakeup();
          }
          key = null;
          // Done. Ready for more. Return to pool
          this.pool.returnWorker(this);
        }
      }
    }

    /**
     * Called to initiate a unit of work by this worker thread on the provided
     * SelectionKey object. This method is synchronized, as is the run( )
     * method, so only one key can be serviced at a given time. Before waking
     * the worker thread, and before returning to the main selection loop, this
     * key's interest set is updated to remove OP_READ. This will cause the
     * selector to ignore read-readiness for this channel while the worker
     * thread is servicing it.
     */
    synchronized void serviceChannel(SelectionKey key) {
      this.key = key;
      key.interestOps(key.interestOps() & (~SelectionKey.OP_READ));
      this.notify(); // Awaken the thread
    }

    /**
     * The actual code which drains the channel associated with the given key.
     * This method assumes the key has been modified prior to invocation to turn
     * off selection interest in OP_READ. When this method completes it
     * re-enables OP_READ and calls wakeup( ) on the selector so the selector
     * will resume watching this channel.
     */
    void drainChannel(SelectionKey key) throws Exception {
      reply(key);
    }
  }

  @Override
  protected void close() {
    super.close();
    pool.shutdown();
  }
}
