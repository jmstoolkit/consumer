/*
 * Copyright 2011, Scott Douglass <scott@swdouglass.com>
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License
 * on the World Wide Web for more details:
 * http://www.fsf.org/licensing/licenses/gpl.txt
 */
package com.jmstoolkit.consumer;

import com.jmstoolkit.beans.MessageTableRecord;
import com.jmstoolkit.JTKException;
import com.jmstoolkit.Settings;
import javax.naming.NamingException;
import org.jdesktop.application.Action;
import org.jdesktop.application.ResourceMap;
import org.jdesktop.application.SingleFrameApplication;
import org.jdesktop.application.FrameView;
import org.jdesktop.application.TaskMonitor;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import javax.jms.ConnectionFactory;
import javax.jms.Destination;
import javax.swing.DefaultComboBoxModel;
import javax.swing.Timer;
import javax.swing.Icon;
import javax.swing.JDialog;
import javax.swing.JFrame;
import org.springframework.jms.connection.CachingConnectionFactory;
import org.springframework.jms.connection.UserCredentialsConnectionFactoryAdapter;
import org.springframework.jndi.JndiTemplate;

/**
 * The application's main frame.
 */
public class ConsumerView extends FrameView {

  private static final String P_CONNECTION_FACTORIES = "connection_factories";
  private static final String P_DESTINATIONS = "destinations";
  private static final String D_JNDI_PROPERTIES = "jndi.properties";
  private JndiTemplate jndiTemplate;
  private List<String> connectionFactoryList = new ArrayList<String>();
  private List<String> destinationList = new ArrayList<String>();
  private Properties appProperties = new Properties();
  private CachingConnectionFactory connectionFactory;

  private void _init() {
    try {
      Settings.loadSystemSettings(
        System.getProperty(D_JNDI_PROPERTIES, D_JNDI_PROPERTIES));
      // load settings from default file: app.properties
      // which contains previously used connection
      // factories and destinations
      Settings.loadSettings(appProperties);
      connectionFactoryList = Settings.getSettings(appProperties, P_CONNECTION_FACTORIES);
      destinationList = Settings.getSettings(appProperties, P_DESTINATIONS);
    } catch (JTKException se) {
      // this happens BEFORE initComponents, so can't put the error in the
      // status area or any other part of the GUI
      System.out.println(se.toStringWithStackTrace());
    }
    // FIXME: Not using the applicationContext at all... ho hum
    this.connectionFactory = new CachingConnectionFactory();
    this.connectionFactory.setCacheProducers(true);
    this.jndiTemplate = new JndiTemplate();
  }

  private ConnectionFactory wrapConnectionFactory(String inJNDIName)
  throws NamingException {
    UserCredentialsConnectionFactoryAdapter uccfa
        = new UserCredentialsConnectionFactoryAdapter();
      uccfa.setUsername(appProperties.getProperty("jmstoolkit.username"));
      uccfa.setPassword(appProperties.getProperty("jmstoolkit.password"));
      uccfa.setTargetConnectionFactory(
        (ConnectionFactory) jndiTemplate.lookup(inJNDIName));
      return uccfa;
  }

  /**
   *
   * @param app
   */
  public ConsumerView(SingleFrameApplication app) {
    super(app);

    _init();
    initComponents();

    // post components, finish inititalization based on initial values
    // of combo boxes
    try {
      this.messageTableModel.setDestination(
        (Destination) this.jndiTemplate.lookup(
          destinationComboBox.getSelectedItem().toString()));
      this.messageTableModel.setConnectionFactory(
        wrapConnectionFactory(
          connectionFactoryComboBox.getSelectedItem().toString()));
    } catch (NamingException ex) {
       messageTextArea.setText(
         JTKException.formatException(ex));
    } catch (NullPointerException e) {
      // if we have no previous properties, we'll get NullPointerException from
      // the .toString()s... but we don't care.
    }

    // status bar initialization - message timeout, idle icon and busy animation, etc
    ResourceMap resourceMap = getResourceMap();
    int messageTimeout = resourceMap.getInteger("StatusBar.messageTimeout");
    messageTimer = new Timer(messageTimeout, new ActionListener() {

      @Override
      public void actionPerformed(ActionEvent e) {
        statusMessageLabel.setText("");
      }
    });
    messageTimer.setRepeats(false);
    int busyAnimationRate = resourceMap.getInteger("StatusBar.busyAnimationRate");
    for (int i = 0; i < busyIcons.length; i++) {
      busyIcons[i] = resourceMap.getIcon("StatusBar.busyIcons[" + i + "]");
    }
    busyIconTimer = new Timer(busyAnimationRate, new ActionListener() {

      @Override
      public void actionPerformed(ActionEvent e) {
        busyIconIndex = (busyIconIndex + 1) % busyIcons.length;
        statusAnimationLabel.setIcon(busyIcons[busyIconIndex]);
      }
    });
    idleIcon = resourceMap.getIcon("StatusBar.idleIcon");
    statusAnimationLabel.setIcon(idleIcon);
    progressBar.setVisible(false);

    // connecting action tasks to status bar via TaskMonitor
    TaskMonitor taskMonitor = new TaskMonitor(getApplication().getContext());
    taskMonitor.addPropertyChangeListener(new java.beans.PropertyChangeListener() {

      @Override
      public void propertyChange(java.beans.PropertyChangeEvent evt) {
        String propertyName = evt.getPropertyName();
        if ("started".equals(propertyName)) {
          if (!busyIconTimer.isRunning()) {
            statusAnimationLabel.setIcon(busyIcons[0]);
            busyIconIndex = 0;
            busyIconTimer.start();
          }
          progressBar.setVisible(true);
          progressBar.setIndeterminate(true);
        } else if ("done".equals(propertyName)) {
          busyIconTimer.stop();
          statusAnimationLabel.setIcon(idleIcon);
          progressBar.setVisible(false);
          progressBar.setValue(0);
        } else if ("message".equals(propertyName)) {
          String text = (String) (evt.getNewValue());
          statusMessageLabel.setText((text == null) ? "" : text);
          messageTimer.restart();
        } else if ("progress".equals(propertyName)) {
          int value = (Integer) (evt.getNewValue());
          progressBar.setVisible(true);
          progressBar.setIndeterminate(false);
          progressBar.setValue(value);
        }
      }
    });
  }

  /**
   *
   */
  @Action
  public void showAboutBox() {
    if (aboutBox == null) {
      JFrame mainFrame = ConsumerApp.getApplication().getMainFrame();
      aboutBox = new ConsumerAboutBox(mainFrame);
      aboutBox.setLocationRelativeTo(mainFrame);
    }
    ConsumerApp.getApplication().show(aboutBox);
  }

  /** This method is called from within the constructor to
   * initialize the form.
   * WARNING: Do NOT modify this code. The content of this method is
   * always regenerated by the Form Editor.
   */
  @SuppressWarnings("unchecked")
  // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
  private void initComponents() {

    mainPanel = new javax.swing.JPanel();
    messagesSplitPane = new javax.swing.JSplitPane();
    messageRecordTableScrollPane = new javax.swing.JScrollPane();
    messageRecordTable = new javax.swing.JTable();
    messagePropertiesSplitPane = new javax.swing.JSplitPane();
    messageTextScrollPane = new javax.swing.JScrollPane();
    messageTextArea = new javax.swing.JTextArea();
    messagePropertiesTableScrollPane = new javax.swing.JScrollPane();
    messagePropertiesTable = new javax.swing.JTable();
    connectionFactoryComboBox = new javax.swing.JComboBox();
    connectionFactoryLabel = new javax.swing.JLabel();
    destinationLabel = new javax.swing.JLabel();
    destinationComboBox = new javax.swing.JComboBox();
    subscribeCheckBox = new javax.swing.JCheckBox();
    menuBar = new javax.swing.JMenuBar();
    javax.swing.JMenu fileMenu = new javax.swing.JMenu();
    clearMenuItem = new javax.swing.JMenuItem();
    javax.swing.JMenuItem exitMenuItem = new javax.swing.JMenuItem();
    javax.swing.JMenu helpMenu = new javax.swing.JMenu();
    javax.swing.JMenuItem aboutMenuItem = new javax.swing.JMenuItem();
    statusPanel = new javax.swing.JPanel();
    javax.swing.JSeparator statusPanelSeparator = new javax.swing.JSeparator();
    statusMessageLabel = new javax.swing.JLabel();
    statusAnimationLabel = new javax.swing.JLabel();
    progressBar = new javax.swing.JProgressBar();
    messageTableModel = new com.jmstoolkit.beans.MessageTableModel();
    messagePropertyTableModel = new com.jmstoolkit.beans.PropertyTableModel();

    mainPanel.setName("mainPanel"); // NOI18N

    messagesSplitPane.setOrientation(javax.swing.JSplitPane.VERTICAL_SPLIT);
    messagesSplitPane.setName("messagesSplitPane"); // NOI18N

    messageRecordTableScrollPane.setDoubleBuffered(true);
    messageRecordTableScrollPane.setName("messageRecordTableScrollPane"); // NOI18N

    messageRecordTable.setModel(messageTableModel);
    messageRecordTable.setCellSelectionEnabled(true);
    messageRecordTable.setDoubleBuffered(true);
    messageRecordTable.setName("messageRecordTable"); // NOI18N
    messageRecordTable.addMouseListener(new java.awt.event.MouseAdapter() {
      public void mouseReleased(java.awt.event.MouseEvent evt) {
        messageRecordTableMouseReleased(evt);
      }
    });
    messageRecordTableScrollPane.setViewportView(messageRecordTable);

    messagesSplitPane.setLeftComponent(messageRecordTableScrollPane);

    messagePropertiesSplitPane.setOrientation(javax.swing.JSplitPane.VERTICAL_SPLIT);
    messagePropertiesSplitPane.setName("messagePropertiesSplitPane"); // NOI18N
    messagePropertiesSplitPane.setPreferredSize(new java.awt.Dimension(454, 200));

    messageTextScrollPane.setName("messageTextScrollPane"); // NOI18N

    messageTextArea.setColumns(20);
    messageTextArea.setLineWrap(true);
    messageTextArea.setRows(5);
    messageTextArea.setTabSize(2);
    org.jdesktop.application.ResourceMap resourceMap = org.jdesktop.application.Application.getInstance(com.jmstoolkit.consumer.ConsumerApp.class).getContext().getResourceMap(ConsumerView.class);
    messageTextArea.setToolTipText(resourceMap.getString("messageTextArea.toolTipText")); // NOI18N
    messageTextArea.setWrapStyleWord(true);
    messageTextArea.setName("messageTextArea"); // NOI18N
    messageTextScrollPane.setViewportView(messageTextArea);

    messagePropertiesSplitPane.setTopComponent(messageTextScrollPane);

    messagePropertiesTableScrollPane.setName("messagePropertiesTableScrollPane"); // NOI18N
    messagePropertiesTableScrollPane.setPreferredSize(new java.awt.Dimension(452, 202));

    messagePropertiesTable.setModel(messagePropertyTableModel);
    messagePropertiesTable.setToolTipText(resourceMap.getString("messagePropertiesTable.toolTipText")); // NOI18N
    messagePropertiesTable.setAutoCreateRowSorter(true);
    messagePropertiesTable.setCellSelectionEnabled(true);
    messagePropertiesTable.setDoubleBuffered(true);
    messagePropertiesTable.setName("messagePropertiesTable"); // NOI18N
    messagePropertiesTableScrollPane.setViewportView(messagePropertiesTable);

    messagePropertiesSplitPane.setRightComponent(messagePropertiesTableScrollPane);

    messagesSplitPane.setRightComponent(messagePropertiesSplitPane);

    connectionFactoryComboBox.setEditable(true);
    connectionFactoryComboBox.setModel(new DefaultComboBoxModel(connectionFactoryList.toArray()));
    javax.swing.ActionMap actionMap = org.jdesktop.application.Application.getInstance(com.jmstoolkit.consumer.ConsumerApp.class).getContext().getActionMap(ConsumerView.class, this);
    connectionFactoryComboBox.setAction(actionMap.get("setConnectionFactory")); // NOI18N
    connectionFactoryComboBox.setName("connectionFactoryComboBox"); // NOI18N

    connectionFactoryLabel.setText(resourceMap.getString("connectionFactoryLabel.text")); // NOI18N
    connectionFactoryLabel.setName("connectionFactoryLabel"); // NOI18N

    destinationLabel.setText(resourceMap.getString("destinationLabel.text")); // NOI18N
    destinationLabel.setName("destinationLabel"); // NOI18N

    destinationComboBox.setEditable(true);
    destinationComboBox.setModel(new DefaultComboBoxModel(destinationList.toArray()));
    destinationComboBox.setAction(actionMap.get("setDestination")); // NOI18N
    destinationComboBox.setName("destinationComboBox"); // NOI18N

    subscribeCheckBox.setAction(actionMap.get("toggleSubscription")); // NOI18N
    subscribeCheckBox.setText(resourceMap.getString("subscribeCheckBox.text")); // NOI18N
    subscribeCheckBox.setToolTipText(resourceMap.getString("subscribeCheckBox.toolTipText")); // NOI18N
    subscribeCheckBox.setHorizontalTextPosition(javax.swing.SwingConstants.LEADING);
    subscribeCheckBox.setName("subscribeCheckBox"); // NOI18N

    javax.swing.GroupLayout mainPanelLayout = new javax.swing.GroupLayout(mainPanel);
    mainPanel.setLayout(mainPanelLayout);
    mainPanelLayout.setHorizontalGroup(
      mainPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
      .addGroup(mainPanelLayout.createSequentialGroup()
        .addContainerGap()
        .addGroup(mainPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
          .addGroup(mainPanelLayout.createSequentialGroup()
            .addComponent(destinationLabel)
            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
            .addComponent(destinationComboBox, 0, 357, Short.MAX_VALUE))
          .addGroup(mainPanelLayout.createSequentialGroup()
            .addComponent(connectionFactoryLabel)
            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
            .addComponent(connectionFactoryComboBox, 0, 312, Short.MAX_VALUE)))
        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
        .addComponent(subscribeCheckBox)
        .addGap(32, 32, 32))
      .addGroup(mainPanelLayout.createSequentialGroup()
        .addGap(1, 1, 1)
        .addComponent(messagesSplitPane, javax.swing.GroupLayout.DEFAULT_SIZE, 590, Short.MAX_VALUE))
    );
    mainPanelLayout.setVerticalGroup(
      mainPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
      .addGroup(mainPanelLayout.createSequentialGroup()
        .addContainerGap()
        .addGroup(mainPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
          .addComponent(connectionFactoryLabel)
          .addComponent(connectionFactoryComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
        .addGroup(mainPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
          .addComponent(destinationLabel)
          .addComponent(destinationComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
          .addComponent(subscribeCheckBox))
        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
        .addComponent(messagesSplitPane, javax.swing.GroupLayout.DEFAULT_SIZE, 238, Short.MAX_VALUE))
    );

    menuBar.setName("menuBar"); // NOI18N

    fileMenu.setText(resourceMap.getString("fileMenu.text")); // NOI18N
    fileMenu.setName("fileMenu"); // NOI18N

    clearMenuItem.setAction(actionMap.get("clearTable")); // NOI18N
    clearMenuItem.setText(resourceMap.getString("clearMenuItem.text")); // NOI18N
    clearMenuItem.setName("clearMenuItem"); // NOI18N
    fileMenu.add(clearMenuItem);

    exitMenuItem.setAction(actionMap.get("quit")); // NOI18N
    exitMenuItem.setName("exitMenuItem"); // NOI18N
    fileMenu.add(exitMenuItem);

    menuBar.add(fileMenu);

    helpMenu.setText(resourceMap.getString("helpMenu.text")); // NOI18N
    helpMenu.setName("helpMenu"); // NOI18N

    aboutMenuItem.setAction(actionMap.get("showAboutBox")); // NOI18N
    aboutMenuItem.setText(resourceMap.getString("aboutMenuItem.text")); // NOI18N
    aboutMenuItem.setName("aboutMenuItem"); // NOI18N
    helpMenu.add(aboutMenuItem);

    menuBar.add(helpMenu);

    statusPanel.setName("statusPanel"); // NOI18N

    statusPanelSeparator.setName("statusPanelSeparator"); // NOI18N

    statusMessageLabel.setText("Recieved: " + messageTableModel.getMessagesReceived().toString());
    statusMessageLabel.setName("statusMessageLabel"); // NOI18N

    statusAnimationLabel.setHorizontalAlignment(javax.swing.SwingConstants.LEFT);
    statusAnimationLabel.setName("statusAnimationLabel"); // NOI18N

    progressBar.setName("progressBar"); // NOI18N

    javax.swing.GroupLayout statusPanelLayout = new javax.swing.GroupLayout(statusPanel);
    statusPanel.setLayout(statusPanelLayout);
    statusPanelLayout.setHorizontalGroup(
      statusPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
      .addComponent(statusPanelSeparator, javax.swing.GroupLayout.DEFAULT_SIZE, 591, Short.MAX_VALUE)
      .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, statusPanelLayout.createSequentialGroup()
        .addGap(9, 9, 9)
        .addComponent(statusMessageLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 362, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 44, Short.MAX_VALUE)
        .addComponent(progressBar, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
        .addComponent(statusAnimationLabel)
        .addContainerGap())
    );
    statusPanelLayout.setVerticalGroup(
      statusPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
      .addGroup(statusPanelLayout.createSequentialGroup()
        .addComponent(statusPanelSeparator, javax.swing.GroupLayout.PREFERRED_SIZE, 2, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        .addGroup(statusPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
          .addComponent(statusMessageLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 20, javax.swing.GroupLayout.PREFERRED_SIZE)
          .addComponent(statusAnimationLabel)
          .addComponent(progressBar, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
        .addGap(3, 3, 3))
    );

    messageTableModel.addTableModelListener(new javax.swing.event.TableModelListener() {
      public void tableChanged(javax.swing.event.TableModelEvent evt) {
        messageTableModelTableChanged(evt);
      }
    });

    setComponent(mainPanel);
    setMenuBar(menuBar);
    setStatusBar(statusPanel);
  }// </editor-fold>//GEN-END:initComponents

    private void messageRecordTableMouseReleased(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_messageRecordTableMouseReleased
      Integer selectedColumn = messageRecordTable.getSelectedColumn();
      Integer selectedRow = messageRecordTable.getSelectedRow();
      this.messageTextArea.setText(
        (String) this.messageRecordTable.getValueAt(selectedRow, selectedColumn));
      MessageTableRecord mRecord = (MessageTableRecord) this.messageTableModel.getData().get(selectedRow);
      this.messagePropertyTableModel.setData(mRecord.getProperties());
      this.messagePropertyTableModel.fireTableDataChanged();
    }//GEN-LAST:event_messageRecordTableMouseReleased

    private void messageTableModelTableChanged(javax.swing.event.TableModelEvent evt) {//GEN-FIRST:event_messageTableModelTableChanged
      statusMessageLabel.setText("Received: " + messageTableModel.getMessagesReceived().toString());
    }//GEN-LAST:event_messageTableModelTableChanged

  // Variables declaration - do not modify//GEN-BEGIN:variables
  private javax.swing.JMenuItem clearMenuItem;
  private javax.swing.JComboBox connectionFactoryComboBox;
  private javax.swing.JLabel connectionFactoryLabel;
  private javax.swing.JComboBox destinationComboBox;
  private javax.swing.JLabel destinationLabel;
  private javax.swing.JPanel mainPanel;
  private javax.swing.JMenuBar menuBar;
  private javax.swing.JSplitPane messagePropertiesSplitPane;
  private javax.swing.JTable messagePropertiesTable;
  private javax.swing.JScrollPane messagePropertiesTableScrollPane;
  private com.jmstoolkit.beans.PropertyTableModel messagePropertyTableModel;
  private javax.swing.JTable messageRecordTable;
  private javax.swing.JScrollPane messageRecordTableScrollPane;
  private com.jmstoolkit.beans.MessageTableModel messageTableModel;
  private javax.swing.JTextArea messageTextArea;
  private javax.swing.JScrollPane messageTextScrollPane;
  private javax.swing.JSplitPane messagesSplitPane;
  private javax.swing.JProgressBar progressBar;
  private javax.swing.JLabel statusAnimationLabel;
  private javax.swing.JLabel statusMessageLabel;
  private javax.swing.JPanel statusPanel;
  private javax.swing.JCheckBox subscribeCheckBox;
  // End of variables declaration//GEN-END:variables
  private final Timer messageTimer;
  private final Timer busyIconTimer;
  private final Icon idleIcon;
  private final Icon[] busyIcons = new Icon[15];
  private int busyIconIndex = 0;
  private JDialog aboutBox;

  /**
   *
   */
  @Action
  public void setConnectionFactory() {
    String selectedItem = connectionFactoryComboBox.getSelectedItem().toString();
    try {
      this.messageTableModel.setConnectionFactory(
        wrapConnectionFactory(selectedItem));
      if (!destinationList.contains(selectedItem)) {
        connectionFactoryList =
          Settings.addSetting(appProperties, P_CONNECTION_FACTORIES, selectedItem);
        connectionFactoryComboBox.addItem(selectedItem);
      }
    } catch (NamingException ex) {
      messageTextArea.setText(JTKException.formatException(ex));
    }
  }

  /**
   *
   */
  @Action
  public void setDestination() {
    String selectedItem = this.destinationComboBox.getSelectedItem().toString();
    try {
      this.messageTableModel.setDestination(
        (Destination) jndiTemplate.lookup(selectedItem));
      if (!destinationList.contains(selectedItem)) {
        destinationList = Settings.addSetting(appProperties, P_DESTINATIONS, selectedItem);
        destinationComboBox.addItem(selectedItem);
      }
    } catch (NamingException ex) {
      messageTextArea.setText(JTKException.formatException(ex));
    }
  }

  /**
   *
   */
  @Action
  public void toggleSubscription() {
    if (this.subscribeCheckBox.isSelected()
      && !this.messageTableModel.isRunning()) {
      this.messageTableModel.start();
    } else if (this.messageTableModel.isRunning()) {
      this.messageTableModel.stop();
    }
  }

  /**
   *
   */
  @Action
  public void clearTable() {
    this.messageTableModel.setData(new ArrayList<MessageTableRecord>());
  }

  /**
   *
   */
  @Action
  public void quit() {
    int code = 0;
    try {
      Settings.saveSettings(appProperties, "Saved.");
    } catch (JTKException e) {
      System.out.println(e.toStringWithStackTrace());
      code = 1;
    } finally {
      this.messageTableModel.stop();
      System.exit(code);
    }
  }
}
