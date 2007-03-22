/*
 * Copyright (c) 2006, Swedish Institute of Computer Science. All rights
 * reserved.
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 1. Redistributions of source code must retain the above copyright notice,
 * this list of conditions and the following disclaimer. 2. Redistributions in
 * binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other
 * materials provided with the distribution. 3. Neither the name of the
 * Institute nor the names of its contributors may be used to endorse or promote
 * products derived from this software without specific prior written
 * permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE INSTITUTE AND CONTRIBUTORS ``AS IS'' AND ANY
 * EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE INSTITUTE OR CONTRIBUTORS BE LIABLE FOR ANY
 * DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 * 
 * $Id: Simulation.java,v 1.10 2007/03/22 20:39:34 fros4943 Exp $
 */

package se.sics.cooja;

import java.util.*;
import org.apache.log4j.Logger;
import org.jdom.*;

import se.sics.cooja.dialogs.*;

/**
 * A simulation consists of a number of motes and mote types.
 * 
 * The motes in the simulation are ticked one by one in a simulation loop. When
 * all motes have been ticked once, the simulation time is updated and then the
 * simulation sleeps for some specified delay time. Any tick observers are also
 * notified at this time.
 * 
 * When observing the simulation itself, the simulation state, added or deleted
 * motes etc. are observed, as opposed to individual mote changes. Changes of
 * individual motes should instead be observed via corresponding mote
 * interfaces.
 * 
 * @author Fredrik Osterlind
 */
public class Simulation extends Observable implements Runnable {

  private Vector<Mote> motes = new Vector<Mote>();

  private Vector<MoteType> moteTypes = new Vector<MoteType>();

  private int delayTime = 5;

  private int currentSimulationTime = 0;

  private int tickTime = 1;

  private String title = null;

  private RadioMedium currentRadioMedium = null;

  private static Logger logger = Logger.getLogger(Simulation.class);

  private boolean isRunning = false;

  private boolean stopSimulation = false;

  private Thread thread = null;

  private GUI myGUI = null;

  // Tick observable
  private class TickObservable extends Observable {
    private void allTicksPerformed() {
      setChanged();
      notifyObservers();
    }
  }

  private TickObservable tickObservable = new TickObservable();

  /**
   * Add tick observer. This observer is notified once every tick loop, that is,
   * when all motes have been ticked.
   * 
   * @see #deleteTickObserver(Observer)
   * @param newObserver
   *          New observer
   */
  public void addTickObserver(Observer newObserver) {
    tickObservable.addObserver(newObserver);
  }

  /**
   * Delete an existing tick observer.
   * 
   * @see #addTickObserver(Observer)
   * @param observer
   *          Observer to delete
   */
  public void deleteTickObserver(Observer observer) {
    tickObservable.deleteObserver(observer);
  }

  public void run() {
    long lastStartTime = System.currentTimeMillis();
    logger.info("Simulation main loop started, system time: " + lastStartTime);
    isRunning = true;

    // Notify observers simulation is starting
    this.setChanged();
    this.notifyObservers(this);

    while (isRunning) {
      try {
        // Tick all motes
        for (Mote moteToTick : motes) {
          moteToTick.tick(currentSimulationTime);
        }

        // Increase simulation time
        currentSimulationTime += tickTime;

        // Notify tick observers
        tickObservable.allTicksPerformed();

        // Sleep
        if (delayTime > 0)
          Thread.sleep(delayTime);

        if (stopSimulation) {
          // We should only tick once (and we just did), so abort now
          stopSimulation = false;
          isRunning = false;
          thread = null;
        }

      } catch (InterruptedException e) {
        isRunning = false;
        thread = null;
        break;
      } catch (IllegalArgumentException e) {
        logger.warn("llegalArgumentException:" + e);
        isRunning = false;
        thread = null;
        break;
      } catch (IllegalMonitorStateException e) {
        logger.warn("IllegalMonitorStateException:" + e);
        isRunning = false;
        thread = null;
        break;
      }
    }

    isRunning = false;
    thread = null;
    stopSimulation = false;

    // Notify observers simulation has stopped
    this.setChanged();
    this.notifyObservers(this);

    logger.info("Simulation main loop stopped, system time: "
        + System.currentTimeMillis() + "\tDuration: "
        + (System.currentTimeMillis() - lastStartTime) + " ms");
  }

  /**
   * Creates a new simulation with a delay time of 100 ms.
   */
  public Simulation(GUI gui) {
    myGUI = gui;
  }

  /**
   * Starts this simulation (notifies observers).
   */
  public void startSimulation() {
    if (!isRunning()) {
      isRunning = true;
      thread = new Thread(this);
      thread.start();
    }
  }

  /**
   * Stops this simulation (notifies observers).
   */
  public void stopSimulation() {
    if (isRunning()) {
      stopSimulation = true;
      thread.interrupt();

      // Wait until simulation stops
      if (Thread.currentThread() != thread)
        while (thread != null && thread.isAlive()) {
          try {
            Thread.sleep(10);
          } catch (InterruptedException e) {
          }
        }
    } 
  }

  /**
   * Starts simulation if stopped, ticks all motes once, and finally stops
   * simulation again.
   */
  public void tickSimulation() {
    stopSimulation = true;

    if (!isRunning()) {
      thread = new Thread(this);
      thread.start();
    }

    // Wait until simulation stops
    while (thread != null && thread.isAlive()) {
      try {
        Thread.sleep(10);
      } catch (InterruptedException e) {
      }
    }

  }

  /**
   * @return GUI holding this simulation
   */
  public GUI getGUI() {
    return myGUI;
  }

  /**
   * Returns the current simulation config represented by XML elements. This
   * config also includes the current radio medium, all mote types and motes.
   * 
   * @return Current simulation config
   */
  public Collection<Element> getConfigXML() {
    Vector<Element> config = new Vector<Element>();

    Element element;

    // Title
    element = new Element("title");
    element.setText(title);
    config.add(element);

    // Delay time
    element = new Element("delaytime");
    element.setText(Integer.toString(delayTime));
    config.add(element);

    // Simulation time
    element = new Element("simtime");
    element.setText(Integer.toString(currentSimulationTime));
    config.add(element);

    // Tick time
    element = new Element("ticktime");
    element.setText(Integer.toString(tickTime));
    config.add(element);

    // Radio Medium
    element = new Element("radiomedium");
    element.setText(currentRadioMedium.getClass().getName());

    Collection radioMediumXML = currentRadioMedium.getConfigXML();
    if (radioMediumXML != null)
      element.addContent(radioMediumXML);
    config.add(element);

    // Mote types
    for (MoteType moteType : getMoteTypes()) {
      element = new Element("motetype");
      element.setText(moteType.getClass().getName());

      Collection moteTypeXML = moteType.getConfigXML();
      if (moteTypeXML != null)
        element.addContent(moteTypeXML);
      config.add(element);
    }

    // Motes
    for (Mote mote : motes) {
      element = new Element("mote");
      element.setText(mote.getClass().getName());

      Collection moteXML = mote.getConfigXML();
      if (moteXML != null)
        element.addContent(moteXML);
      config.add(element);
    }

    return config;
  }

  /**
   * Sets the current simulation config depending on the given XML elements.
   * 
   * @see #getConfigXML()
   * @param configXML
   *          Config XML elements
   * @param visAvailable
   *          True if simulation is allowed to show visualizers while loading
   *          the given config
   * @return True if simulation config set successfully
   * @throws Exception
   *           If configuration could not be loaded
   */
  public boolean setConfigXML(Collection<Element> configXML,
      boolean visAvailable) throws Exception {

    // Parse elements
    for (Element element : configXML) {

      // Title
      if (element.getName().equals("title")) {
        title = element.getText();
      }

      // Delay time
      if (element.getName().equals("delaytime")) {
        delayTime = Integer.parseInt(element.getText());
      }

      // Simulation time
      if (element.getName().equals("simtime")) {
        currentSimulationTime = Integer.parseInt(element.getText());
      }

      // Tick time
      if (element.getName().equals("ticktime")) {
        tickTime = Integer.parseInt(element.getText());
      }

      // Radio medium
      if (element.getName().equals("radiomedium")) {
        String radioMediumClassName = element.getText().trim();
        Class<? extends RadioMedium> radioMediumClass = myGUI.tryLoadClass(
            this, RadioMedium.class, radioMediumClassName);

        if (radioMediumClass != null) {
          // Create radio medium specified in config
          try {
            currentRadioMedium = RadioMedium.generateRadioMedium(
                radioMediumClass, this);
          } catch (Exception e) {
            currentRadioMedium = null;
            logger.warn("Could not load radio medium class: "
                + radioMediumClassName);
          }
        }

        // Show configure simulation dialog
        boolean createdOK = false;
        if (visAvailable) {
          createdOK = CreateSimDialog.showDialog(GUI.frame, this);
        } else {
          createdOK = true;
        }

        if (!createdOK) {
          logger.debug("Simulation not created, aborting");
          throw new Exception("Load aborted by user");
        }

        // Check if radio medium specific config should be applied
        if (radioMediumClassName
            .equals(currentRadioMedium.getClass().getName())) {
          currentRadioMedium.setConfigXML(element.getChildren(), visAvailable);
        } else {
          logger
              .info("Radio Medium changed - ignoring radio medium specific config");
        }
      }

      // Mote type
      if (element.getName().equals("motetype")) {
        String moteTypeClassName = element.getText().trim();

        Class<? extends MoteType> moteTypeClass = myGUI.tryLoadClass(this,
            MoteType.class, moteTypeClassName);

        if (moteTypeClass == null) {
          logger.fatal("Could not load mote type class: " + moteTypeClassName);
          return false;
        }

        MoteType moteType = moteTypeClass.getConstructor((Class[]) null)
            .newInstance();

        boolean createdOK = moteType.setConfigXML(this, element.getChildren(),
            visAvailable);
        if (createdOK) {
          addMoteType(moteType);
        } else {
          logger
              .fatal("Mote type was not created: " + element.getText().trim());
          throw new Exception("All mote types were not recreated");
        }
      }

      // Mote
      if (element.getName().equals("mote")) {
        Class<? extends Mote> moteClass = myGUI.tryLoadClass(this, Mote.class,
            element.getText().trim());

        Mote mote = moteClass.getConstructor((Class[]) null).newInstance();
        if (mote.setConfigXML(this, element.getChildren(), visAvailable)) {
          addMote(mote);
        } else {
          logger.fatal("Mote was not created: " + element.getText().trim());
          throw new Exception("All motes were not recreated");
        }
      }
    }

    setChanged();
    notifyObservers(this);
    return true;
  }

  /**
   * Removes a mote from this simulation
   * 
   * @param mote
   *          Mote to remove
   */
  public void removeMote(Mote mote) {
    if (isRunning()) {
      stopSimulation();
      motes.remove(mote);
      startSimulation();
    } else
      motes.remove(mote);

    currentRadioMedium.unregisterMote(mote, this);
    this.setChanged();
    this.notifyObservers(this);
  }

  /**
   * Adds a mote to this simulation
   * 
   * @param mote
   *          Mote to add
   */
  public void addMote(Mote mote) {
    if (isRunning()) {
      stopSimulation();
      motes.add(mote);
      startSimulation();
    } else
      motes.add(mote);

    currentRadioMedium.registerMote(mote, this);
    this.setChanged();
    this.notifyObservers(this);
  }

  /**
   * Get a mote from this simulation.
   * 
   * @param pos
   *          Internal list position of mote
   * @return Mote
   */
  public Mote getMote(int pos) {
    return motes.get(pos);
  }

  /**
   * Returns number of motes in this simulation.
   * 
   * @return Number of motes
   */
  public int getMotesCount() {
    return motes.size();
  }

  /**
   * Returns all mote types in simulation.
   * 
   * @return All mote types
   */
  public Vector<MoteType> getMoteTypes() {
    return moteTypes;
  }

  /**
   * Returns mote type with given identifier.
   * 
   * @param identifier
   *          Mote type identifier
   * @return Mote type or null if not found
   */
  public MoteType getMoteType(String identifier) {
    for (MoteType moteType : getMoteTypes()) {
      if (moteType.getIdentifier().equals(identifier))
        return moteType;
    }
    return null;
  }

  /**
   * Adds given mote type to simulation.
   * 
   * @param newMoteType Mote type
   */
  public void addMoteType(MoteType newMoteType) {
    moteTypes.add(newMoteType);

    this.setChanged();
    this.notifyObservers(this);
  }

  /**
   * Set delay time to delayTime. When all motes have been ticked, the
   * simulation waits for this time before ticking again.
   * 
   * @param delayTime
   *          New delay time (ms)
   */
  public void setDelayTime(int delayTime) {
    this.delayTime = delayTime;

    this.setChanged();
    this.notifyObservers(this);
  }

  /**
   * Returns current delay time.
   * 
   * @return Delay time (ms)
   */
  public int getDelayTime() {
    return delayTime;
  }

  /**
   * Set simulation time to simulationTime.
   * 
   * @param simulationTime
   *          New simulation time (ms)
   */
  public void setSimulationTime(int simulationTime) {
    currentSimulationTime = simulationTime;

    this.setChanged();
    this.notifyObservers(this);
  }

  /**
   * Returns current simulation time.
   * 
   * @return Simulation time (ms)
   */
  public int getSimulationTime() {
    return currentSimulationTime;
  }

  /**
   * Set tick time to tickTime. The tick time is the simulated time every tick
   * takes. When all motes have been ticked, current simulation time is
   * increased with tickTime. Default tick time is 1 ms.
   * 
   * @see #getTickTime()
   * @see #getTickTimeInSeconds()
   * @param tickTime
   *          New tick time (ms)
   */
  public void setTickTime(int tickTime) {
    this.tickTime = tickTime;

    this.setChanged();
    this.notifyObservers(this);
  }

  /**
   * Changes radio medium of this simulation to the given.
   * 
   * @param radioMedium
   *          New radio medium
   */
  public void setRadioMedium(RadioMedium radioMedium) {
    // Remove current radio medium from observing motes
    if (currentRadioMedium != null)
      for (int i = 0; i < motes.size(); i++)
        currentRadioMedium.unregisterMote(motes.get(i), this);

    // Change current radio medium to new one
    if (radioMedium == null) {
      logger.fatal("Radio medium could not be created!");
      return;
    }
    this.currentRadioMedium = radioMedium;

    // Add all current motes to the new radio medium
    for (int i = 0; i < motes.size(); i++)
      currentRadioMedium.registerMote(motes.get(i), this);
  }

  /**
   * Get currently used radio medium.
   * 
   * @return Currently used radio medium
   */
  public RadioMedium getRadioMedium() {
    return currentRadioMedium;
  }

  /**
   * Get current tick time (ms).
   * 
   * @see #setTickTime(int)
   * @return Current tick time (ms)
   */
  public int getTickTime() {
    return tickTime;
  }

  /**
   * Get current tick time (seconds).
   * 
   * @see #setTickTime(int)
   * @return Current tick time (seconds)
   */
  public double getTickTimeInSeconds() {
    return ((double) tickTime) / 1000.0;
  }

  /**
   * Return true is simulation is running.
   * 
   * @return True if simulation is running
   */
  public boolean isRunning() {
    return isRunning && thread != null;
  }

  /**
   * Get current simulation title (short description).
   * 
   * @return Title
   */
  public String getTitle() {
    return title;
  }

  /**
   * Set simulation title.
   * 
   * @param title
   *          New title
   */
  public void setTitle(String title) {
    this.title = title;
  }

}
