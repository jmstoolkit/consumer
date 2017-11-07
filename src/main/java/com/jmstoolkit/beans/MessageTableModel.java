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
package com.jmstoolkit.beans;

import java.text.SimpleDateFormat;
import java.util.Enumeration;
import java.util.Properties;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.jms.ConnectionFactory;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageListener;
import javax.jms.ObjectMessage;
import javax.jms.TextMessage;
import javax.swing.table.AbstractTableModel;
import org.springframework.jms.listener.DefaultMessageListenerContainer;

/**
 *
 * @author Scott Douglass
 */
public class MessageTableModel extends AbstractTableModel implements MessageListener {

  private static final Logger LOGGER = Logger.getLogger(MessageTableModel.class.getName());
  private static final SimpleDateFormat DATE_TIME = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

  /**
   *
   */
  public static final String PROP_DATA = "data";
  private List<MessageTableRecord> data = new ArrayList<>();
  private String[] COLUMN_NAME = new String[]{
    "Timestamp",
    "Message ID",
    "Correlation ID",
    "Reply To",
    "Priority",
    "Delivery Mode",
    "Expiration",
    "Type",
    "Text"
  };
  private Integer messagesReceived = 0;
  private final DefaultMessageListenerContainer listenerContainer = 
    new DefaultMessageListenerContainer();

  /**
   *
   */
  public MessageTableModel() {
  }

  /**
   *
   */
  public void start() {
    if (!listenerContainer.isRunning()) {
      listenerContainer.setMessageListener(this);
      LOGGER.info("Starting listener...");
      listenerContainer.initialize();
      listenerContainer.start();
      if (!listenerContainer.isRunning()) {
        LOGGER.log(Level.SEVERE, "Message Listener Container did  not start.");
      }
    }
  }

  /**
   *
   */
  public void stop() {
    if (listenerContainer.isRunning()) {
      LOGGER.info("Stopping listener...");
      listenerContainer.stop(new Stop());
      listenerContainer.shutdown();
    }
  }

  /**
   * @return the messagesReceived
   */
  public Integer getMessagesReceived() {
    return messagesReceived;
  }

  private class Stop implements Runnable {

    @Override
    public void run() {
      LOGGER.info("MessageListener shut down.");
    }
  }

  /**
   *
   * @return
   */
  public Boolean isRunning() {
    return this.listenerContainer.isRunning();
  }

  /**
   *
   * @param destination
   */
  public void setDestination(Destination destination) {
    listenerContainer.setDestination(destination);
  }

  /**
   *
   * @param connectionFactory
   */
  public void setConnectionFactory(ConnectionFactory connectionFactory) {
    listenerContainer.setConnectionFactory(connectionFactory);
  }

  /**
   *
   * @return
   */
  public List getData() {
    return data;
  }

  /**
   *
   * @param value
   */
  public void setData(List<MessageTableRecord> value) {
    List oldData = data;
    data = value;
    this.fireTableDataChanged();
  }

  @Override
  public int getRowCount() {
    return this.data.size();
  }

  @Override
  public int getColumnCount() {
    return COLUMN_NAME.length;
  }

  @Override
  public String getColumnName(int column) {
    return COLUMN_NAME[column];
  }

  /**
   * @return the COLUMN_NAME
   */
  public String[] getCOLUMN_NAME() {
    return COLUMN_NAME;
  }

  /**
   * @param aColumnName the COLUMN_NAME to set
   */
  public void setCOLUMN_NAME(String[] aColumnName) {
    COLUMN_NAME = aColumnName;
  }

  @Override
  public Object getValueAt(int rowIndex, int columnIndex) {
    Object[] record = data.toArray();
    MessageTableRecord qRecord = (MessageTableRecord) record[rowIndex];
    String result = "";
    try {
      switch (columnIndex) {
        case 0:
          result = DATE_TIME.format(qRecord.getJMSTimestamp());
          break;
        case 1:
          result = qRecord.getJMSMessageID();
          break;
        case 2:
          result = qRecord.getJMSCorrelationID();
          break;
        case 3:
          result = "";
          break;
        case 4:
          result = Integer.toString(qRecord.getJMSPriority());
          break;
        case 5:
          result = Integer.toString(qRecord.getJMSDeliveryMode());
          break;
        case 6:
          result = Long.toString(qRecord.getJMSExpiration());
          break;
        case 7:
          result = qRecord.getJMSType();
          break;
        case 8:
          result = qRecord.getText();
          break;
      }
    } catch (JMSException e) {
      LOGGER.log(Level.WARNING, "JMS problem", e);
    }
    return result;
  }

  /**
   *
   * @param message
   */
  @Override
  public void onMessage(Message message) {
    LOGGER.log(Level.INFO, "Message Received");
    messagesReceived++;
    try {
      MessageTableRecord qRecord = new MessageTableRecord();
      qRecord.setJMSCorrelationID(message.getJMSCorrelationID());
      qRecord.setJMSDeliveryMode(message.getJMSDeliveryMode());
      qRecord.setJMSExpiration(message.getJMSExpiration());
      qRecord.setJMSMessageID(message.getJMSMessageID());
      qRecord.setJMSTimestamp(message.getJMSTimestamp());
      qRecord.setJMSType(message.getJMSType());
      qRecord.setJMSDestination(message.getJMSDestination());
      qRecord.setJMSCorrelationIDAsBytes(message.getJMSCorrelationIDAsBytes());
      qRecord.setJMSPriority(message.getJMSPriority());
      qRecord.setJMSType(message.getJMSType());
      qRecord.setJMSReplyTo(message.getJMSReplyTo());
      qRecord.setJMSRedelivered(message.getJMSRedelivered());

      Enumeration propertyEnumerator = message.getPropertyNames();
      Properties props = new Properties();
      while (propertyEnumerator.hasMoreElements()) {
        String pElement = (String) propertyEnumerator.nextElement();
        if (!(pElement == null || pElement.isEmpty())) {
          props.put(pElement, message.getStringProperty(pElement));
        }
      }
      qRecord.setProperties(props);


      if (message instanceof TextMessage) {
        qRecord.setText(((TextMessage) message).getText());
      }
      if (message instanceof ObjectMessage) {
        qRecord.setObject(((ObjectMessage) message).getObject());
      }

      List newData = data;
      newData.add(qRecord);
      this.setData(newData);
    } catch (JMSException e) {
      LOGGER.log(Level.WARNING, "JMS problem", e);
    }
  }
}
