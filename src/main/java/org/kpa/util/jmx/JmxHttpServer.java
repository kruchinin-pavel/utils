package org.kpa.util.jmx;

import com.sun.jdmk.comm.HtmlAdaptorServer;
import org.kpa.util.CachedVal;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import javax.management.*;
import java.lang.management.ManagementFactory;

/**
 * Created by krucpav on 05.08.14.
 */
public class JmxHttpServer {
    private MBeanServer server = null;
    private int port = 800;
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
    }

    public void stop() {
        if (adapter != null && adapter.isActive()) {
            adapter.stop();
        }
    }

    private static final CachedVal<AbstractApplicationContext> init = new CachedVal<>(() -> {
        String springFile = "jmx.export.xml";
        AbstractApplicationContext applicationContext;
        if (springFile.toLowerCase().startsWith("file:")) {
            applicationContext = new ClassPathXmlApplicationContext(new String[]{springFile});
        } else {
            applicationContext = new ClassPathXmlApplicationContext(new String[]{"classpath:" + springFile});
        }
        applicationContext.registerShutdownHook();
        return applicationContext;
    });

    public static void initialize() {
        System.setProperty("jmx.port", "8009");
        init.get();
    }
}