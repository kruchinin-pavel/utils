package org.kpa.util.jmx;

import com.sun.jdmk.comm.HtmlAdaptorServer;
import org.kpa.util.Props;
import org.kpa.util.RunOnce;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import javax.management.*;
import java.lang.management.ManagementFactory;

/**
 * Created by krucpav on 05.08.14.
 */
public class JmxHttpServer {
    private static final Logger log = LoggerFactory.getLogger(JmxHttpServer.class);
    private MBeanServer server = null;
    private int port;
    private String adapterName;

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getAdapterName() {
        return adapterName;
    }

    public void setAdapterName(String adapterName) {
        this.adapterName = adapterName;
    }

    private HtmlAdaptorServer adapter;

    public void start() throws MalformedObjectNameException, MBeanRegistrationException, InstanceAlreadyExistsException, NotCompliantMBeanException {
        if (server == null) {
            server = ManagementFactory.getPlatformMBeanServer();
        }
        adapter = new HtmlAdaptorServer();
        adapter.setPort(port);
        ObjectName adapterObjName = new ObjectName(adapterName + ":name=htmladapter,port=" + port);
        server.registerMBean(adapter, adapterObjName);
        adapter.start();
        log.info("JMX HTTP Server started at port: {}", port);
        Runtime.getRuntime().addShutdownHook(new Thread(this::stop));
    }

    public void stop() {
        if (adapter != null && adapter.isActive()) {
            log.info("JMX HTTP Server stopped at port: {}", port);
            adapter.stop();
        }
    }

    private static final RunOnce init = new RunOnce();

    public static void initialize() {
        init.runOnce(() -> {
            try {
                String springFile = "jmx.export.xml";
                AbstractApplicationContext applicationContext;
                if (springFile.toLowerCase().startsWith("file:")) {
                    applicationContext = new ClassPathXmlApplicationContext(new String[]{springFile});
                } else {
                    applicationContext = new ClassPathXmlApplicationContext(new String[]{"classpath:" + springFile});
                }
                applicationContext.registerShutdownHook();
                applicationContext.start();
                JmxHttpServer server = new JmxHttpServer();
                server.setAdapterName("MBean");
                int portNumber = Integer.parseInt(Props.getProperty("jmx.port", "JMX ser ver port number",
                        "8080"));
                server.setPort(portNumber);
                server.start();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }
}