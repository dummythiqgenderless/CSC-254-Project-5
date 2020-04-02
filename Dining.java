// Simple Java implementation of the classic Dining Philosophers problem.
//
// No synchronization (yet).
//
// Graphics are *very* naive.  Philosophers are big blobs.
// Forks are little blobs.
//
// Written by Michael Scott, 1997; updated 2013 to use Swing.
// Updated again in 2019 to drop support for applets.
//

import java.awt.*;
import java.awt.event.*;
import java.io.*;
import javax.swing.*;
import java.util.*;
import java.lang.*;
import javax.imageio.ImageIO; 
import java.awt.image.ImageObserver;
import java.awt.image.BufferedImage; 
import java.lang.Thread.*;
import java.util.concurrent.locks.ReentrantLock;

// This code has six main classes:
//  Dining
//      The public, "main" class.
//  Philosopher
//      Active -- extends Thread
//  Fork
//      Passive
//  Table
//      Manages the philosophers and forks and their physical layout.
//  Coordinator
//      Provides mechanisms to suspend, resume, and reset the state of
//      worker threads (philosophers).
//  UI
//      Manages graphical layout and button presses.

public class Dining {
    private static final int CANVAS_SIZE = 360;
        // pixels in each direction;
        // needs to agree with size in dining.html
    public static boolean verbose = false;

    public static void main(String[] args) {
        //if "-v" is passed onto the command, print out messages.
        if((args.length > 0) && args[0].equals("-v")){
            Dining.verbose = true;
        }
        JFrame f = new JFrame("Dining");
        f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        Dining me = new Dining();

        final Coordinator c = new Coordinator();
        final Table t = new Table(c, CANVAS_SIZE);
        // arrange to call graphical setup from GUI thread
        try {
            SwingUtilities.invokeAndWait(new Runnable() {
                public void run() {
                    new UI(f, c, t);
                }
            });
        } catch (Exception e) {
            System.err.println("unable to create GUI");
        }

        f.pack();            // calculate size of frame
        f.setVisible(true);
    }
}

class Fork extends JPanel implements ImageObserver {
    //for each resource, create a lock so that it is not acquired by 2
    //processes at the same time
    public ReentrantLock lock = new ReentrantLock(true);
    private Table t;
    private static final int XSIZE = 10;
    private static final int YSIZE = 10;
    private int orig_x;
    private int orig_y;
   	int x;
    int y;

    // Constructor.
    // cx and cy indicate coordinates of center.
    // Note that fillOval method expects coordinates of upper left corner
    // of bounding box instead.
    //
    public Fork(Table T, int cx, int cy) {
      t = T;
      orig_x = cx;
      orig_y = cy;
      x = cx;
      y = cy;
    }

    public void reset() {
        clear();
        x = orig_x;
        y = orig_y;
        t.repaint();
    }

    // arguments are coordinates of acquiring philosopher's center
    //
    public void acquire(int px, int py) {
      clear();
      x = (orig_x + px)/2;
      y = (orig_y + py)/2;
      t.repaint();
    }

    public void release() {
      //when release it, the resource is not restricted to only one process any more.
        lock.unlock();
        reset();
    }

    // render self
    //
    public void draw(Graphics g, Image image) {
        //g.setColor(Color.black);
        //g.fillOval(x-XSIZE/2, y-YSIZE/2, XSIZE, YSIZE);
        g.drawImage(image, x-40/2, y-40/2, this);
    }
    // erase self
    //
    private void clear() {
        Graphics g = t.getGraphics();
        g.setColor(t.getBackground());
        g.fillOval(x-XSIZE/2, y-YSIZE/2, XSIZE, YSIZE);
    }
}

class Philosopher extends Thread {
    public int philosopherNum;
    private static final Color THINK_COLOR = Color.blue;
    private static final Color WAIT_COLOR = Color.red;
    private static final Color EAT_COLOR = Color.green;
    private static final double THINK_TIME = 4.0;
    private static final double FUMBLE_TIME = 2.0;
        // time between becoming hungry and grabbing first fork
    private static final double EAT_TIME = 3.0;

    private Coordinator c;
    private Table t;
    private static final int XSIZE = 50;
    private static final int YSIZE = 50;
    private int x;
    private int y;
    private Fork left_fork;
    private Fork right_fork;
    private Random prn;
    private Color color;

    //Used to indicate the left and right fork are held for each philosopher
    boolean left = false;
    boolean right = false;

    // Constructor.
    // cx and cy indicate coordinates of center
    // Note that fillOval method expects coordinates of upper left corner
    // of bounding box instead.
    //add a field called philosopherNum to keep track of the order of the philosopher
    public Philosopher(Table T, int cx, int cy,
                      Fork lf, Fork rf, Coordinator C, int philosopherNum) {
        t = T;
        x = cx;
        y = cy;
        left_fork = lf;
        right_fork = rf;
        c = C;
        prn = new Random();
        color = THINK_COLOR;
        this.philosopherNum = philosopherNum;
    }

    // start method of Thread calls run; you don't
    //
    public void run() {
        for (;;) {
            try {
                if (c.gate()) delay(EAT_TIME/2.0);
                think();
                if (c.gate()) delay(THINK_TIME/2.0);
                hunger();
                if (c.gate()) delay(FUMBLE_TIME/2.0);
                eat();
            } catch(ResetException e) {
            	

               // g.drawImage(T.bluephilosopher, x-40/2, y-40/2, this);
                color = THINK_COLOR;
                t.repaint();
            }
        }
    }

    // render self
    public void draw(Graphics g, int width, int height) {

        g.setColor(color);
        g.fillOval(x-XSIZE/2, y-YSIZE/2, XSIZE, YSIZE);
    	
    }

    // sleep for secs +- FUDGE (%) seconds
    //
    private static final double FUDGE = 0.2;
    private void delay(double secs) throws ResetException {
        double ms = 1000 * secs;
        int window = (int) (2.0 * ms * FUDGE);
        int add_in = prn.nextInt() % window;
        int original_duration = (int) ((1.0-FUDGE) * ms + add_in);
        int duration = original_duration;
        for (;;) {
            try {
                Thread.sleep(duration);
                return;
            } catch(InterruptedException e) {
                if (c.isReset()) {
                    throw new ResetException();
                } else {        // suspended
                    c.gate();   // wait until resumed
                    duration = original_duration / 2;
                    // don't wake up instantly; sleep for about half
                    // as long as originally instructed
                }
            }
        }
    }

    private void think() throws ResetException {
      //print out info
      if(Dining.verbose == true){
          System.out.print("Philosopher " + philosopherNum + " thinking\n");
      }
        //t.philType = t.bluephilosopher; 
        color = THINK_COLOR;
        t.repaint();
        delay(THINK_TIME);

    }

    private void hunger() throws ResetException {
      //print out info
      if(Dining.verbose == true){
          System.out.print("Philosopher " + philosopherNum + " waiting\n");
      }
        // t.philType = t.redphilosopher; 
        color = WAIT_COLOR;
        t.repaint();
        delay(FUMBLE_TIME);
        //Call the correction method, this is used to resolve deadlock issues
        //this method prevents two philosphers grabbing the same fork at the same time
        correction();

    }

    private void eat() throws ResetException {
      //a philosopher can only eat if they have both left and right fork held.
      if (left && right) {
        if(Dining.verbose == true){
            System.out.print("Philosopher " + philosopherNum + " eating\n");
        }
          // t.philType = t.greenphilosopher; 
          color = EAT_COLOR;
          t.repaint();
          delay(EAT_TIME);
          left_fork.release();
          //set back the boolean after the philosopher is done eating, same for right
          left = false;
          yield();    // you aren't allowed to remove this
          right_fork.release();
          right = false;
      }
    }

    private void correction() {
    //if the left fork of this philosopher is available, acquire the left fork
      if(left_fork.lock.tryLock()) {
          left_fork.acquire(x, y);
          //set the left fork to true because it is held same for the right
          left = true;
          yield();    // you aren't allowed to remove this
          //if left is held and right is available, acquire the right fork as well
          if((left && right_fork.lock.tryLock())) {
            right_fork.acquire(x, y);
            right = true;
          }
          //solving the deadlock; came to Prof Scott's OH, and he assisted with this reasoning
          //aka, if this philosopher is holding the left fork for long enough but right fork is
          //still not available, then there is a deadlock; the philosopher might as well put down
          //the fork
           else  {
             left_fork.lock.unlock();
             left = false;
          }
      }
    }

}

// Graphics panel in which philosophers and forks appear.
//
class Table extends JPanel {
    private static final int NUM_PHILS = 5;

    // following fields are set by construcctor:
    private final Coordinator c;
    private Fork[] forks;
    private Philosopher[] philosophers;

    public Image unscaled;
	public Image image; 
	public Image spaghetti; 
	public Image spaghettiunscaled;
	public Image defaultphilosopherunscaled;
	public Image defaultphilosopher; 
	public Image redphilosopherunscaled;
	public Image redphilosopher; 
	public Image bluephilosopherunscaled;
	public Image bluephilosopher; 
	public Image greenphilosopherunscaled;
	public Image greenphilosopher; 
	public Image philosopher;  
	public Image philType = defaultphilosopher;



    public void pause() {
        c.pause();
        // force philosophers to notice change in coordinator state:
        for (int i = 0; i < NUM_PHILS; i++) {
            philosophers[i].interrupt();
        }
    }

    // Called by the UI when it wants to start over.
    //
    public void reset() {
        c.reset();
        // force philosophers to notice change in coordinator state:
        for (int i = 0; i < NUM_PHILS; i++) {
            philosophers[i].interrupt();
        }
        for (int i = 0; i < NUM_PHILS; i++) {
            forks[i].reset();
        }
    }

    // The following method is called automatically by the graphics
    // system when it thinks the Table canvas needs to be re-displayed.
    // This can happen because code elsewhere in this program called
    // repaint(), or because of hiding/revealing or open/close
    // operations in the surrounding window system.
    //

    public void paintComponent(Graphics g) {
        super.paintComponent(g);
        g.drawImage(spaghetti, (getWidth()-70)/2, (getHeight()-60)/2, this);
        for (int i = 0; i < NUM_PHILS; i++) {
        	Fork fork = forks[i];
        	int x = fork.x;
        	int y = fork.y;
        	//g.drawImage(image, x-40/2, y-40/2, this);

        	//g.drawImage(philType, x-40/2, y-40/2, this);
            forks[i].draw(g, image);
            int height = getHeight();
   			int width  = getWidth();
            philosophers[i].draw(g, width, height);
        }
        g.setColor(Color.black);
        
        /*

        g.setColor(Color.black);
        g.fillOval(x-XSIZE/2, y-YSIZE/2, XSIZE, YSIZE);

        */
        g.drawRect(0, 0, getWidth()-1, getHeight()-1);
    }

    // Constructor
    //
    // Note that angles are measured in radians, not degrees.
    // The origin is the upper left corner of the frame.
    //
    public Table(Coordinator C, int CANVAS_SIZE) {    // constructor
    	setBackground(Color.white);
        c = C;
        forks = new Fork[NUM_PHILS];
        philosophers = new Philosopher[NUM_PHILS];
        setPreferredSize(new Dimension(CANVAS_SIZE, CANVAS_SIZE));


        	try { 
        		//For forks 
        	  	unscaled = ImageIO.read(new File("fork.png"));
        	  	image = unscaled.getScaledInstance(40, 40, Image.SCALE_DEFAULT);

        	  	//For the spaghetti 
        	  	spaghettiunscaled = ImageIO.read(new File("spaghetti.png"));
        	  	spaghetti = spaghettiunscaled.getScaledInstance(70, 70, Image.SCALE_DEFAULT);

        	  	/*

        	  	defaultphilosopherunscaled = ImageIO.read(new File("defaultphilosopher.png"));
        	  	defaultphilosopher = defaultphilosopherunscaled.getScaledInstance(70, 70, Image.SCALE_DEFAULT);
      		
        	  	//For the philosophers -> red, green, and blue  
        	  	redphilosopherunscaled = ImageIO.read(new File("redphilosopher.png"));
        	  	redphilosopher = redphilosopherunscaled.getScaledInstance(70, 70, Image.SCALE_DEFAULT);
      		
        	  	bluephilosopherunscaled = ImageIO.read(new File("bluephilosopher.png"));
        	  	bluephilosopher = bluephilosopherunscaled.getScaledInstance(70, 70, Image.SCALE_DEFAULT);
      		
        	  	greenphilosopherunscaled = ImageIO.read(new File("greenphilosopher.png"));
        	  	greenphilosopher = greenphilosopherunscaled.getScaledInstance(70, 70, Image.SCALE_DEFAULT);

        	  	*/
      		
      		 } catch (IOException ex) {
           			 // Some sort of exception handling 
       		}
      

        for (int i = 0; i < NUM_PHILS; i++) {
            double angle = Math.PI/2 + 2*Math.PI/NUM_PHILS*(i-0.5);
            forks[i] = new Fork(this,
                (int) (CANVAS_SIZE/2.0 + CANVAS_SIZE/6.0 * Math.cos(angle)),
                (int) (CANVAS_SIZE/2.0 - CANVAS_SIZE/6.0 * Math.sin(angle)));
        }
        for (int i = 0; i < NUM_PHILS; i++) {
            double angle = Math.PI/2 + 2*Math.PI/NUM_PHILS*i;
            //note that a new "philosopherNum" field is added to the constructor;
            //also add one to each philosopher number because it starts from one
            philosophers[i] = new Philosopher(this,
                (int) (CANVAS_SIZE/2.0 + CANVAS_SIZE/3.0 * Math.cos(angle)),
                (int) (CANVAS_SIZE/2.0 - CANVAS_SIZE/3.0 * Math.sin(angle)),
                forks[i],
                forks[(i+1) % NUM_PHILS],
                c, i+1);
            philosophers[i].start();


        }
    }
}

class ResetException extends Exception { };

// The Coordinator serves to slow down execution, so that behavior is
// visible on the screen, and to notify all running threads when the user
// wants them to reset.
//
class Coordinator {
    public enum State { PAUSED, RUNNING, RESET }
    private State state = State.PAUSED;

    public synchronized boolean isPaused() {
        return (state == State.PAUSED);
    }

    public synchronized void pause() {
        state = State.PAUSED;
    }

    public synchronized boolean isReset() {
        return (state == State.RESET);
    }

    public synchronized void reset() {
        state = State.RESET;
    }

    public synchronized void resume() {
        state = State.RUNNING;
        notifyAll();        // wake up all waiting threads
    }

    // Return true if we were forced to wait because the coordinator was
    // paused or reset.
    //
    public synchronized boolean gate() throws ResetException {
        if (state == State.PAUSED || state == State.RESET) {
            try {
                wait();
            } catch(InterruptedException e) {
                if (isReset()) {
                    throw new ResetException();
                }
            }
            return true;        // waited
        }
        return false;           // didn't wait
    }
}

// Class UI is the user interface.  It displays a Table canvas above
// a row of buttons.  Actions (event handlers) are defined for each of
// the buttons.  Depending on the state of the UI, either the "run" or
// the "pause" button is the default (highlighted in most window
// systems); it will often self-push if you hit carriage return.
//
class UI extends JPanel {
    private final Coordinator c;
    private final Table t;

    private final JRootPane root;
    private static final int externalBorder = 6;

    private static final int stopped = 0;
    private static final int running = 1;
    private static final int paused = 2;

    private int state = stopped;

    // Constructor
    //
    public UI(RootPaneContainer pane, Coordinator C, Table T) {
        final UI u = this;
        c = C;
        t = T;

        final JPanel b = new JPanel();   // button panel

        final JButton runButton = new JButton("RUN");
        final JButton pauseButton = new JButton("PAUSE");
        final JButton resetButton = new JButton("RESET");
        final JButton quitButton = new JButton("QUIT");

        runButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                c.resume();
                root.setDefaultButton(pauseButton);
            }
        });
        pauseButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                t.pause();
                root.setDefaultButton(runButton);
            }
        });
        resetButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                t.reset();
                root.setDefaultButton(runButton);
            }
        });
        quitButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                System.exit(0);
            }
        });

        // put the buttons into the button panel:
        b.setLayout(new FlowLayout());
        b.add(runButton);
        b.add(pauseButton);
        b.add(resetButton);
        b.add(quitButton);

        

        runButton.setFont(new Font("Arial", Font.BOLD, 12));
        pauseButton.setFont(new Font("Arial", Font.BOLD, 12));
        resetButton.setFont(new Font("Arial", Font.BOLD, 12));
        quitButton.setFont(new Font("Arial", Font.BOLD, 12));


        b.setBackground(Color.black);



        
        runButton.setBackground(Color.WHITE);
         runButton.setForeground(Color.BLACK);

          pauseButton.setBackground(Color.WHITE);
         pauseButton.setForeground(Color.BLACK);

          resetButton.setBackground(Color.WHITE);
         resetButton.setForeground(Color.BLACK);

          quitButton.setBackground(Color.WHITE);
         quitButton.setForeground(Color.BLACK);

         

        // put the Table canvas and the button panel into the UI:
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(BorderFactory.createEmptyBorder(
            externalBorder, externalBorder, externalBorder, externalBorder));
        add(t);
        add(b);

        // put the UI into the Frame
        pane.getContentPane().add(this);
        root = getRootPane();
        root.setDefaultButton(runButton);
    }
}
