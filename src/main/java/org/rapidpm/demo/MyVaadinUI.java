package org.rapidpm.demo;

import com.vaadin.addon.charts.Chart;
import com.vaadin.addon.charts.model.*;
import com.vaadin.addon.charts.model.style.GradientColor;
import com.vaadin.addon.charts.model.style.SolidColor;
import com.vaadin.annotations.Push;
import com.vaadin.annotations.Theme;
import com.vaadin.annotations.VaadinServletConfiguration;
import com.vaadin.server.VaadinRequest;
import com.vaadin.server.VaadinServlet;
import com.vaadin.shared.communication.PushMode;
import com.vaadin.ui.*;
import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import javax.servlet.annotation.WebInitParam;
import javax.servlet.annotation.WebServlet;

@Theme("mytheme")
@SuppressWarnings("serial")
@Push(value = PushMode.AUTOMATIC)
public class MyVaadinUI extends UI {

  @WebServlet(value = "/*", asyncSupported = true, initParams = {
      @WebInitParam(name="pushmode", value = "automatic")
  })
  @VaadinServletConfiguration(productionMode = false, ui = MyVaadinUI.class, widgetset = "org.rapidpm.demo.AppWidgetSet")
  public static class Servlet extends VaadinServlet {
  }


  final static String TOPIC = "TinkerForge/Wetterstation/";

  private String lastMessage = "start:-:-:0";
  private Chart chart = (Chart) getChart();
  private MqttClient empf;
  private MQQT_Thread mqqtThread = new MQQT_Thread();




  @Override
  protected void init(VaadinRequest request) {
    System.out.println("request = " + request);
    final VerticalLayout layout = new VerticalLayout();
    layout.setMargin(true);
    setContent(layout);
    layout.addComponent(chart);

    try {
      empf = new MqttClient("tcp://192.168.0.106:1883", "MyfirstMQTTEmpf",
          new MemoryPersistence());
      empf.setCallback(new MqttCallback() {
        @Override
        public void connectionLost(Throwable throwable) { }
        @Override
        public void messageArrived(String str, MqttMessage mqttMessage) throws Exception {
          byte[] payload = mqttMessage.getPayload();
          lastMessage = new String(payload);
          System.out.println("s = " + str + " msg " + lastMessage);
        }
        @Override
        public void deliveryComplete(IMqttDeliveryToken iMqttDeliveryToken) { }
      });

      Button button = new Button("refresh");
      button.addClickListener(event -> access(() -> chart.getConfiguration()
          .getSeries()
          .forEach(s -> ((ListSeries) s).updatePoint(0, Double.valueOf(lastMessage.split(":")[3])))));
      layout.addComponent(button);
      empf.connect();
      empf.subscribe(TOPIC, 1);

      mqqtThread.start();
    } catch (MqttException e) {
      e.printStackTrace();
    }
  }

  protected Component getChart() {
    final Chart chart = new Chart();
    chart.setWidth("500px");

    final Configuration configuration = new Configuration();
    configuration.getChart().setType(ChartType.GAUGE);
    configuration.getChart().setAlignTicks(false);
    configuration.getChart().setPlotBackgroundColor(null);
    configuration.getChart().setPlotBackgroundImage(null);
    configuration.getChart().setPlotBorderWidth(0);
    configuration.getChart().setPlotShadow(false);
    configuration.setTitle("Temperature");

    configuration.getPane().setStartAngle(-150);
    configuration.getPane().setEndAngle(150);

    YAxis yAxis = new YAxis();
    yAxis.setMin(-30);
    yAxis.setMax(50);
    yAxis.setLineColor(new SolidColor("#339"));
    yAxis.setTickColor(new SolidColor("#339"));
    yAxis.setMinorTickColor(new SolidColor("#339"));
    yAxis.setOffset(-25);
    yAxis.setLineWidth(2);
    yAxis.setLabels(new Labels());
    yAxis.getLabels().setDistance(-20);
    yAxis.getLabels().setRotationPerpendicular();
    yAxis.setTickLength(5);
    yAxis.setMinorTickLength(5);
    yAxis.setEndOnTick(false);

    configuration.addyAxis(yAxis);

    final ListSeries series = new ListSeries("Temperature", 12);

    PlotOptionsGauge plotOptionsGauge = new PlotOptionsGauge();
    plotOptionsGauge.setDataLabels(new Labels());
    plotOptionsGauge
        .getDataLabels()
        .setFormatter("function() {return '' + this.y +  ' °C';}");
    GradientColor gradient = GradientColor.createLinear(0, 0, 0, 1);
    gradient.addColorStop(0, new SolidColor("#DDD"));
    gradient.addColorStop(1, new SolidColor("#FFF"));
    plotOptionsGauge.getDataLabels().setBackgroundColor(gradient);
    plotOptionsGauge.getTooltip().setValueSuffix(" °C");
    series.setPlotOptions(plotOptionsGauge);
    configuration.setSeries(series);
    chart.drawChart(configuration);

    return chart;
  }

  class MQQT_Thread extends Thread {
    String lastPushed = "";
    @Override
    public void run() {
      try {
        while (true) {
          // Update the data for a while
          Thread.sleep(10000);
          if (!lastPushed.equals(lastMessage)) {
            access(() -> chart.getConfiguration()
                .getSeries()
                .forEach(s -> {
                  Double newValue = Double.valueOf(lastMessage.split(":")[3]);
                  ((ListSeries) s).updatePoint(0, newValue);
                }));
            System.out.println("pushed -> lastMessage = " + lastMessage);
            lastPushed = lastMessage;
          }
        }
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
    }
  }

}
