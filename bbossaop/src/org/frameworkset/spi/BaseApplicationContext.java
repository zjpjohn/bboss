/*
 *  Copyright 2008 biaoping.yin
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.frameworkset.spi;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;
import java.util.StringTokenizer;

import org.apache.log4j.Logger;
import org.frameworkset.spi.assemble.BaseTXManager;
import org.frameworkset.spi.assemble.BeanAccembleHelper;
import org.frameworkset.spi.assemble.BeanInf;
import org.frameworkset.spi.assemble.LinkConfigFile;
import org.frameworkset.spi.assemble.Pro;
import org.frameworkset.spi.assemble.ProArray;
import org.frameworkset.spi.assemble.ProList;
import org.frameworkset.spi.assemble.ProMap;
import org.frameworkset.spi.assemble.ProSet;
import org.frameworkset.spi.assemble.ProviderManagerInfo;
import org.frameworkset.spi.assemble.RefID;
import org.frameworkset.spi.assemble.ServiceProviderManager;
import org.frameworkset.spi.assemble.callback.AssembleCallback;
import org.frameworkset.spi.cglib.BaseCGLibProxy;
import org.frameworkset.spi.cglib.CGLibProxy;
import org.frameworkset.spi.cglib.CGLibUtil;
import org.frameworkset.spi.cglib.SimpleCGLibProxy;
import org.frameworkset.spi.cglib.SynCGLibProxy;
import org.frameworkset.spi.cglib.SynTXCGLibProxy;
import org.frameworkset.spi.remote.Header;
import org.frameworkset.spi.remote.Headers;
import org.frameworkset.spi.remote.ServiceID;
import org.frameworkset.spi.security.SecurityContext;
import org.frameworkset.spi.security.SecurityManager;
import org.frameworkset.spi.support.DelegatingMessageSource;
import org.frameworkset.spi.support.HotDeployResourceBundleMessageSource;
import org.frameworkset.spi.support.MessageSource;
import org.frameworkset.spi.support.MessageSourceResolvable;
import org.frameworkset.spi.support.NoSuchMessageException;
import org.frameworkset.util.Assert;
import org.frameworkset.util.io.DefaultResourceLoader;
import org.frameworkset.util.io.PathMatchingResourcePatternResolver;
import org.frameworkset.util.io.Resource;
import org.frameworkset.util.io.ResourceLoader;
import org.frameworkset.util.io.ResourcePatternResolver;

import com.frameworkset.proxy.InvocationHandler;
import com.frameworkset.proxy.ProxyFactory;
import com.frameworkset.spi.assemble.BeanInstanceException;
import com.frameworkset.util.SimpleStringUtil;


/**
 * <p>Title: BaseApplicationContext.java</p> 
 * <p>Description: </p>
 * <p>bboss workgroup</p>
 * <p>Copyright (c) 2007</p>
 * @Date 2011-5-11 上午09:52:42
 * @author biaoping.yin
 * @version 1.0
 */
public abstract class  BaseApplicationContext extends DefaultResourceLoader implements
		MessageSource, ResourcePatternResolver, ResourceLoader {
	
	/**
	 * 定义5种组件容器的类型代码
	 */
	public static int container_type_simple = 0;
	public static int container_type_application = 1;
	public static int container_type_soa = 2;
	public static int container_type_soafile = 3;
	public static int container_type_mvc = 4;
	public static String mvccontainer_identifier = "webmvc";
	protected static Map<String, BaseApplicationContext> applicationContexts = new HashMap<String, BaseApplicationContext>();
	public Map<String, ServiceID> serviceids = new java.util.WeakHashMap<String, ServiceID>();
	protected static List<String> rootFiles = new ArrayList<String>();
	protected boolean started = true;
	/**
	 * 指定容器中的组件缺省使用单例还是多实例模式
	 */
	protected boolean default_singable = true;
	protected boolean needRecordFile = true;
	protected boolean isfile = true;
	/**
	 * Name of the MessageSource bean in the factory. If none is supplied,
	 * message resolution is delegated to the parent.
	 * 
	 * @see MessageSource
	 */
	public static final String MESSAGE_SOURCE_BEAN_NAME = "messageSource";
	
	/**
	 * 获取指定根配置文件上下文bean组件管理容器，配置文件从参数configfile对应配置文件开始
	 * 不同的上下文件环境容器互相隔离，组件间不存在依赖关系，属性也不存在任何引用关系。
	 * 
	 * @return
	 */
	public static BaseApplicationContext getBaseApplicationContext(String configfile) {
		if (configfile == null || configfile.equals("")) {
			log.debug("configfile is null or empty.default Config File["
					+ ServiceProviderManager.defaultConfigFile
					+ "] will be used. ");
			configfile = ServiceProviderManager.defaultConfigFile;
		}
		BaseApplicationContext instance = applicationContexts.get(configfile);
		if (instance != null)
		{
			instance.initApplicationContext();
			return instance;
		}
		
		return instance;
	}
	
	private static Method getApplicationContextMethod = null;
	private static final Object lock_getApplicationContextMethod = new Object();
	
	private static Method initGetApplicationContextMethod() throws SecurityException, NoSuchMethodException, ClassNotFoundException
	{
		if(getApplicationContextMethod != null)
		{
			return getApplicationContextMethod;
		}
		else
		{
			synchronized(lock_getApplicationContextMethod)
			{
				if(getApplicationContextMethod != null)
				{
					return getApplicationContextMethod;
				}
				Class clazz =  Class.forName("org.frameworkset.spi.ApplicationContext");
				Method m = clazz.getDeclaredMethod("getApplicationContext",String.class);
				getApplicationContextMethod = m;
			}
		}
		return getApplicationContextMethod;
	}
	public static  BaseApplicationContext getBaseApplicationContext(String applicationContextPath,int containerType)
    {
    	if(containerType == BaseApplicationContext.container_type_application)
    	{
    		try
			{
				
				Method m = initGetApplicationContextMethod();
				return (BaseApplicationContext)m.invoke(null, applicationContextPath);
			}
			
			catch (Exception e)
			{
				throw new RuntimeException(e);
			}
    	}
    	else if(containerType == BaseApplicationContext.container_type_simple)
    	{
    		try
			{
				
				return (DefaultApplicationContext.getApplicationContext(applicationContextPath));
			}
			
			catch (Exception e)
			{
				throw new RuntimeException(e);
			}
    	}
    	else if(containerType == BaseApplicationContext.container_type_mvc)
    	{
    		try
			{
				
				return (BaseApplicationContext.getBaseApplicationContext(BaseApplicationContext.mvccontainer_identifier));
			}
			
			catch (Exception e)
			{
				throw new RuntimeException(e);
			}
    	}
    	throw new RuntimeException("获取应用上下文容器失败,不支持的类型：applicationContextPath["+applicationContextPath+"],containerType=" + containerType);
    	
    		
    }

	public boolean defaultsingable()
	{
		return default_singable;
	}
	public void destroy() {
		destroy(false);
	}
	
	public void destroy(boolean clearContext) {
		if (!started)
			return;

		if (serviceids != null)
			this.serviceids.clear();
		if (servicProviders != null)
			this.servicProviders.clear();
		// this.rootFiles.clear();
		if (singleDestorys != null)
			this.singleDestorys.clear();
		if (destroyServiceMethods != null)
			this.destroyServiceMethods.clear();
		if (providerManager != null)
			this.providerManager.destroy();
		if(clearContext)
			applicationContexts.remove(this.getConfigfile());
		this.applicationContextInited = false;
		this.messageSource = null;
		this.resourcePatternResolver = null;
		this.started = false;
	}

	public boolean stoped() {
		return !this.started;
	}

	public static List<String> getRootFiles() {
		return rootFiles;
	}

	protected String configfile;

	protected BaseApplicationContext(String configfile) {
		// if (configfile == null || configfile.equals(""))
		// throw new NullPointerException(
		// "build ApplicationContext failed:configfile is "
		// + configfile);
		// this.configfile = configfile;
		// rootFiles.add(configfile);
		// providerManager = new ServiceProviderManager(this);
		// providerManager.init(this.configfile);
		this(AssembleCallback.classpathprex, "", configfile);
	}
	
	protected BaseApplicationContext(String content,boolean isfile) {
		// if (configfile == null || configfile.equals(""))
		// throw new NullPointerException(
		// "build ApplicationContext failed:configfile is "
		// + configfile);
		// this.configfile = configfile;
		// rootFiles.add(configfile);
		// providerManager = new ServiceProviderManager(this);
		// providerManager.init(this.configfile);
		
		this(AssembleCallback.classpathprex, "", (String)content,isfile);
	}
	protected BaseApplicationContext(String docbaseType, String docbase,
			String configfile)
	{
		this(docbaseType, docbase,
				configfile,true);
	}
	protected BaseApplicationContext(String docbaseType, String docbase,
			String configfile,boolean isfile) {
		if (configfile == null || configfile.equals(""))
			throw new NullPointerException(
					"build ApplicationContext failed:configfile is "
							+ configfile);
		this.isfile = isfile;
		
		
		if(isfile)
		{
			this.configfile = configfile;
			rootFiles.add(configfile);
		}
		else			
		{
			this.needRecordFile = false;
		}
		providerManager = new ServiceProviderManager(this);
		providerManager.init(docbaseType, docbase, configfile);
		
	}
	
	public BaseApplicationContext(String docbaseType, String docbase,
			InputStream instream, boolean isfile) {
		if (instream == null )
			throw new NullPointerException(
					"build ApplicationContext failed:instream is null.");
		this.isfile = isfile;
		
		
		if(isfile)
		{
			
//			rootFiles.add(configfile);
		}
		else			
		{
			this.needRecordFile = false;
		}
		providerManager = new ServiceProviderManager(this);
		providerManager.init(docbaseType, docbase, 
				instream);
	}
	
	public BaseApplicationContext(String docbaseType, String docbase,
			URL instream, boolean isfile) {
		if (instream == null )
			throw new NullPointerException(
					"build ApplicationContext failed:instream is null.");
		this.isfile = isfile;
		
		
		if(isfile)
		{
			
			this.configfile = instream.getFile();
			rootFiles.add(configfile);
		}
		else			
		{
			this.needRecordFile = false;
		}
		providerManager = new ServiceProviderManager(this);
//		providerManager.init(docbaseType, docbase, 
//				instream);
		providerManager.init(docbaseType, "", configfile,instream);
	}
	
	public BaseApplicationContext(URL file, String path)
	{
		if (file == null )
			throw new NullPointerException(
					"build ApplicationContext failed:configfile is "
							+ null);
		this.isfile = false;
		this.configfile = path;
		this.needRecordFile = false;
		providerManager = new ServiceProviderManager(this);
		providerManager.init(AssembleCallback.classpathprex, "", configfile,file);
	}
	public BaseApplicationContext(InputStream instream, boolean isfile) {
		this(AssembleCallback.classpathprex, "", (InputStream)instream,isfile);
	}
	
	public boolean isfile()
	{
		return this.isfile;
	}
	
	public boolean needRecordFile()
	{
		return this.needRecordFile;
	}
	
	protected Object initlock = new Object();
	protected boolean applicationContextInited = false;
	protected void initApplicationContext()
	{
		if(applicationContextInited)
			return;
		synchronized(initlock)
		{
			if(applicationContextInited)
				return;
			this.resourcePatternResolver = this.getResourcePatternResolver();
			initMessageSource();
			this.applicationContextInited = true;
		}
		
	}

	protected ServiceProviderManager providerManager;
	protected static Object lock = new Object();

//	/**
//	 * 获取默认上下文的bean组件管理容器，配置文件从manager-provider.xml文件开始
//	 * 
//	 * @return
//	 */
//	public static BaseApplicationContext getBaseApplicationContext() {
//		return getBaseApplicationContext(null);
//	}

	private static String aop_proxy_type = null;
	private static final String[] aop_webservice_scope_default = new String[] {"mvc","application","default"};
	private static String[] aop_webservice_scope = aop_webservice_scope_default;
	
	
	public static final String aop_proxy_type_cglib = "cglib";
	public static final String aop_proxy_type_javaproxy = "javaproxy";
	public static final String aop_proxy_type_default = aop_proxy_type_cglib;

	protected static final String aop_proxy_type_key = "aop.proxy.type";
	protected static final String aop_webservice_scope_key = "aop.webservice.scope";
	
	
	protected static final String AOP_PROPERTIES_PATH = "/aop.properties";
	
	/**
	 * Fill the given properties from the given resource.
	 * 
	 * @param props
	 *            the Properties instance to fill
	 * @param resource
	 *            the resource to load from
	 * @throws IOException
	 *             if loading failed
	 */
	public static Properties fillProperties() throws IOException {
//		InputStream is = getInputStream(BaseApplicationContext.class);
//		try {
//			Properties props = new Properties();
//			props.load(is);
//			return props;
//
//		} finally {
//			is.close();
//		}
		return SimpleStringUtil.getProperties(SimpleStringUtil.AOP_PROPERTIES_PATH, BaseApplicationContext.class);
	}
//	public static InputStream getInputStream(Class clazz) throws IOException {
//		InputStream is = null;
//
//		is = clazz.getResourceAsStream(AOP_PROPERTIES_PATH);
//		if (is == null) {
//			throw new FileNotFoundException(AOP_PROPERTIES_PATH
//					+ " cannot be opened because it does not exist");
//		}
//		return is;
//	}
	public static String[] getAopWebserviceScope() {
		if (aop_webservice_scope == null) {
			Properties pro = null;
			try {
				pro = fillProperties();
				String aop_webservice_scope_ = pro.getProperty(aop_webservice_scope_key);
				if(aop_webservice_scope_ != null && !aop_webservice_scope_.trim().equals(""))
				{
					aop_webservice_scope = aop_webservice_scope_.split(",");
				}
				
			} catch (Exception e) {
				log.warn(e.getMessage(),e);
				aop_webservice_scope = aop_webservice_scope_default;
			}
			
		}
		return aop_webservice_scope;
	}
	
	public static String getAOPProxyType() {
		if (aop_proxy_type == null) {
			Properties pro = null;
			try {
				pro = fillProperties();
				aop_proxy_type = pro.getProperty(aop_proxy_type_key, aop_proxy_type_cglib);
				if (aop_proxy_type.equals(aop_proxy_type_cglib))
					aop_proxy_type = aop_proxy_type_cglib;
				else
					aop_proxy_type = aop_proxy_type_default;
			} catch (Exception e) {
				log.warn(e.getMessage(),e);
				aop_proxy_type = aop_proxy_type_default;
			}
			
		}
		return aop_proxy_type;
	}
	
	public static long getSQLFileRefreshInterval() {
		
			Properties pro = null;
			try {
				pro = fillProperties();
				String SQLFileRefreshInterval = pro.getProperty("sqlfile.refresh_interval", "5000");
				return Long.parseLong(SQLFileRefreshInterval);
				
			} catch (Exception e) {
				log.warn(e.getMessage(),e);
				return 5000;
			}
			
		
	
	}
	
	public static long getResourceFileRefreshInterval() {
		
		Properties pro = null;
		try {
			pro = fillProperties();
			String ResourceFileRefreshInterval = pro.getProperty("resourcefile.refresh_interval", "5000");
			return Long.parseLong(ResourceFileRefreshInterval);
			
		} catch (Exception e) {
			log.warn(e.getMessage(),e);
			return 5000l;
		}
		
	

}

//	/**
//	 * 获取指定根配置文件上下文bean组件管理容器，配置文件从参数configfile对应配置文件开始
//	 * 不同的上下文件环境容器互相隔离，组件间不存在依赖关系，属性也不存在任何引用关系。
//	 * 
//	 * @return
//	 */
//	public static BaseApplicationContext getBaseApplicationContext(String configfile) {
//		if (configfile == null || configfile.equals("")) {
//			log.debug("configfile is null or empty.default Config File["
//					+ ServiceProviderManager.defaultConfigFile
//					+ "] will be used. ");
//			configfile = ServiceProviderManager.defaultConfigFile;
//		}
//		BaseApplicationContext instance = applicationContexts.get(configfile);
//		if (instance != null)
//		{
//			instance.initApplicationContext();
//			return instance;
//		}
//		synchronized (lock) {
//			instance = applicationContexts.get(configfile);
//			if (instance != null)
//				return instance;
//			instance = new ApplicationContext(configfile);
//			ApplicationContext.addShutdownHook(new BeanDestroyHook(instance));
//			applicationContexts.put(configfile, instance);
//			
//
//		}
//		instance.initApplicationContext();
//		return instance;
//	}
	private static List<Runnable> shutdownHooks = new ArrayList<Runnable>();
	private static void addShutdownHook_(Runnable destroyVMHook)
	{
		shutdownHooks.add(destroyVMHook);
	}
	
	/**
	 * invoke shutdown hooks by programs when application is undeployed.  
	 */
	public static void shutdown()
	{
		if(shutdownHooks != null)
		{
			for(Runnable destroyVMHook:shutdownHooks)
			{
				try {
					destroyVMHook.run();
					Thread.sleep(1000);
				} catch (Throwable e) {
					log.warn("execute shutdown hook error:", e);
				}
			}
			shutdownHooks.clear();
			shutdownHooks = null;
		}
		
		if(applicationContexts!= null){
			Iterator<Entry<String, BaseApplicationContext>> it = applicationContexts.entrySet().iterator();
			while(it.hasNext())
			{
				Entry<String, BaseApplicationContext> entry = it.next();
				entry.getValue().destroy();
			}
			applicationContexts.clear();
			applicationContexts = null;
		}
		
		rootFiles.clear();
		
	}
	
	static class WrapperRunnable implements Runnable
	{
		private Runnable executor;
		WrapperRunnable(Runnable executor)
		{
			this.executor = executor;
		}
		private boolean executed = false;
		public void run()
		{
			if(executed)
				return;
			
			this.executor.run();
			executed = true;
		}
		
	}
	/**
	 * 添加系统中停止时的回调程序
	 * 
	 * @param destroyVMHook
	 */
	public static void addShutdownHook(Runnable destroyVMHook) {
		try {
			// use reflection and catch the Exception to allow PoolMan to work
			// with 1.2 VM's
			destroyVMHook = new WrapperRunnable(destroyVMHook);
			Class r = Runtime.getRuntime().getClass();
			java.lang.reflect.Method m = r.getDeclaredMethod("addShutdownHook",
					new Class[] { Thread.class });
			m.invoke(Runtime.getRuntime(), new Object[] { new Thread(
					destroyVMHook) });
		} catch (Exception e) {
			e.printStackTrace();
		}
		addShutdownHook_(destroyVMHook);
	}

	public void destroySingleBeans() {
		if (singleDestorys != null && singleDestorys.size() > 0) {
			//			
			Iterator<DisposableBean> ite = singleDestorys.iterator();
			while (ite.hasNext()) {
				DisposableBean et = ite.next();
				try {
					et.destroy();
				} catch (Exception e) {
					log.error(e.getMessage(),e);
				}
			}
		}

		if (destroyServiceMethods != null && destroyServiceMethods.size() > 0) {
			Iterator<DestroyMethod> ite = destroyServiceMethods.iterator();
			for (; ite.hasNext();) {
				DestroyMethod entry = ite.next();
				Object value = entry.getInstance();
				String method = entry.getDestroyMethod();
				try {
					Method m = value.getClass().getDeclaredMethod(method);
					m.invoke(value);
				} catch (SecurityException e) {
					log.error(e.getMessage(),e);
				} catch (NoSuchMethodException e) {
					log.error(e);
				} catch (Exception e) {
					log.error(e.getMessage(),e);
				}
			}
		}
	}

	public void registDisableBean(DisposableBean disposableBean) {
		singleDestorys.add(disposableBean);
	}
	
	private static class DestroyMethod
	{
		
		private String destroyMethod;
		
		private  Object instance;
		public DestroyMethod(String destroyMethod, Object instance) {
			super();
			
			this.destroyMethod = destroyMethod;
			this.instance = instance;
		}
		/**
		 * @return the destroyMethod
		 */
		public String getDestroyMethod() {
			return destroyMethod;
		}
		/**
		 * @return the instance
		 */
		public Object getInstance() {
			return instance;
		}
		
	}

	public void registDestroyMethod(String destroyMethod, Object instance) {
		DestroyMethod destoryMethod = new DestroyMethod(destroyMethod, instance);
		destroyServiceMethods.add(destoryMethod);
	}

	private static Logger log = Logger.getLogger(BaseApplicationContext.class);

	/**
	 * 缺省接口key
	 */
	public static final String DEFAULT_CACHE_KEY = "DEFAULT_CACHE_KEY";

	/**
	 * 同步缓冲key
	 */

	public static final String SYNCHRO_CACHE_KEY = "SYNCHRO_CACHE_KEY";

	/**
	 * 创建不具有同步控制的provider代理接口实例 该代理接口可能有事务控制的功能也可能没有有事务控制，根据具体的配置来决定 createInf
	 * 
	 * @return Object
	 */
	public static Object createInf(final CallContext callcontext,
			final BaseTXManager providerManagerInfo, final Object delegate,
			final ServiceID serviceID) {
		if (BaseApplicationContext.getAOPProxyType() != BaseApplicationContext.aop_proxy_type_cglib) {
			return ProxyFactory.createProxy(new InvocationHandler(delegate) {
				public Object invoke(Object proxy, Method method, Object[] args)
						throws Throwable {
					return CGLibUtil.invoke(delegate, method, args, null,
							callcontext, serviceID, providerManagerInfo);
				}
			});
		} else {
			CGLibProxy proxy = new CGLibProxy(delegate, callcontext, serviceID,
					providerManagerInfo);
			return CGLibUtil.getBeanInstance(delegate.getClass(), delegate
					.getClass(), proxy);
			// return CGLibUtil.forCreateInf(proxy, method, args, null,
			// callcontext, serviceID, providerManagerInfo);
		}
	}
	
	
	/**
	 * 创建不具有同步控制的provider代理接口实例 该代理接口可能有事务控制的功能也可能没有有事务控制，根据具体的配置来决定 createInf
	 * 
	 * @return Object
	 */
	public static Object createInf(
			final Pro providerManagerInfo, final Object delegate) {
		if (BaseApplicationContext.getAOPProxyType() != BaseApplicationContext.aop_proxy_type_cglib) {
			return ProxyFactory.createProxy(new InvocationHandler(delegate) {
				public Object invoke(Object proxy, Method method, Object[] args)
						throws Throwable {
					return CGLibUtil.invoke(delegate, method, args, null,
							 providerManagerInfo);
				}
			});
		} else {
			BaseCGLibProxy proxy = new SimpleCGLibProxy(delegate,
					providerManagerInfo);
			return CGLibUtil.getBeanInstance(delegate.getClass(), delegate
					.getClass(), proxy);
			// return CGLibUtil.forCreateInf(proxy, method, args, null,
			// callcontext, serviceID, providerManagerInfo);
		}
	}
	
	
	 public Object proxyObject(Pro providerManagerInfo,Object refvalue,String refid)
	    {
	    	if (providerManagerInfo.enableTransaction()
					|| providerManagerInfo.enableAsyncCall()
					|| providerManagerInfo.usedCustomInterceptor()) {
	    		if (refid != null && providerManagerInfo.isSinglable()) {
					Object provider = servicProviders.get(refid);
					if (provider != null)
						return provider;
					synchronized (servicProviders) {
						provider = servicProviders.get(refid);
						if (provider != null)
							return provider;
						provider = createInf( providerManagerInfo,
								refvalue);
						servicProviders.put(refid, provider);
					}
					return provider;
				} else {
					refvalue = createInf( providerManagerInfo,
							refvalue);
					return refvalue;
				}
			} else {
				return refvalue;
			}
	    }

	/**
	 * 创建没有同步但有事务控制的provider代理接口实例 该方法的实现逻辑目前和createInf方法一致
	 * 
	 * @return Object
	 */
	protected static Object createTXInf(final CallContext callcontext,
			final BaseTXManager providerManagerInfo, final Object delegate,
			ServiceID serviceID) {
		return createInf(callcontext, providerManagerInfo, delegate, serviceID);

	}

	/**
	 * 创建即具有同步控制功能但不具备事务管理功能的provider代理接口实例 createInf
	 * 
	 * @return Object
	 */
	protected static Object createSynInf(final CallContext callcontext,
			final ProviderManagerInfo providerManagerInfo,
			final Object delegate, final ServiceID serviceID) {
		if (BaseApplicationContext.getAOPProxyType() != BaseApplicationContext.aop_proxy_type_cglib) {
			return ProxyFactory.createProxy(new InvocationHandler(delegate) {
				public Object invoke(Object proxy, Method method, Object[] args)
						throws Throwable {
					return CGLibUtil.invokeSyn(delegate, method, args, null,
							callcontext, serviceID, providerManagerInfo);
				}
			});
		} else {
			SynCGLibProxy proxy = new SynCGLibProxy(delegate,
					providerManagerInfo, serviceID, callcontext);
			return CGLibUtil.getBeanInstance(delegate.getClass(), delegate
					.getClass(), proxy);
		}
	}

	/**
	 * 创建具有同步和事务控制的provider代理接口实例 createInf
	 * 
	 * @return Object
	 */
	protected static Object createSynTXInf(final CallContext callcontext,
			final ProviderManagerInfo providerManagerInfo,
			final Object delegate, final ServiceID serviceID) {
		if (BaseApplicationContext.getAOPProxyType() != BaseApplicationContext.aop_proxy_type_cglib) {
			return ProxyFactory.createProxy(new InvocationHandler(delegate) {
				public Object invoke(Object proxy, Method method, Object[] args)
						throws Throwable {
					return CGLibUtil.invokeSynTX(delegate, method, args, null,
							callcontext, serviceID, providerManagerInfo);

				}
			});
		} else {
			SynTXCGLibProxy proxy = new SynTXCGLibProxy(delegate,
					providerManagerInfo, serviceID, callcontext);
			return CGLibUtil.getBeanInstance(delegate.getClass(), delegate
					.getClass(), proxy);
		}
	}

	/**
	 * 通用特定服务提供接口获取类
	 * 
	 * @param providerManagerType
	 *            String
	 * @return Object
	 * @throws SPIException
	 */
	public Object getProvider(String providerManagerType) throws SPIException {
		return getProvider(providerManagerType, null);
	}

	/**
	 * 获取全局缺省的provider
	 * 
	 * @return Object
	 * @throws SPIException
	 */
	public Object getProvider() throws SPIException {
		return getProvider(null, null);
	}

	// /**
	// * 通用特定服务类型中特定数据源实现的提供接口获取类
	// * @param providerManagerType String
	// * @param sourceType String
	// * @return Provider
	// * @throws SPIException
	// */
	// public static Provider getProvider(String providerManagerType,String
	// sourceType) throws SPIException
	// {
	// ProviderManagerInfo providerManagerInfo = null;
	// if(providerManagerType != null)
	// providerManagerInfo = ConfigManager.getInstance().
	// getProviderManagerInfo(providerManagerType);
	// else
	// {
	// providerManagerInfo =
	// ConfigManager.getInstance().getDefaultProviderManagerInfo();
	// providerManagerType = providerManagerInfo.getId() ;
	// }
	// //各spi管理者的缓冲关键字为providerManagerType ＋ ":" + sourceType;
	// String cacheKey = sourceType != null?providerManagerType+":"+sourceType
	// :providerManagerType+":"+DEFAULT_CACHE_KEY;
	// String syncacheKey = providerManagerType+":"+SYNCHRO_CACHE_KEY;
	//
	// Provider provider = null;
	// //判断是否允许为单实例模式，如果是获取单实例，否则重新创建provider实例
	// if(providerManagerInfo.isSinglable())
	// {
	// provider = (Provider) providers.get(cacheKey);
	// if (provider == null) {
	// try {
	// if (sourceType == null) {
	// provider =
	// (Provider) providerManagerInfo.
	// getDefaulProviderInfo().
	// getProvider();
	// } else {
	// provider =
	// (Provider) providerManagerInfo.
	// getSecurityProviderInfoByType(sourceType).
	// getProvider();
	//
	// }
	// providers.put(cacheKey, provider);
	// } catch (Exception e) {
	// throw new SPIException(
	// "Failed to get UserManagement class instance..."
	// + e.toString());
	// }
	// }
	// }
	// else
	// {
	// if (sourceType == null)
	// provider = providerManagerInfo.
	// getDefaulProviderInfo().getProvider();
	// else
	// provider = providerManagerInfo.
	// getSecurityProviderInfoByType(sourceType)
	// .getProvider();
	// }
	// //如果开启同步机制，获取同步代理接口，否则直接返回缺省的管理接口
	// if (providerManagerInfo.isSynchronizedEnabled()) {
	// Provider synProvider = null;
	// //如果是单实例则获取代理单实例，否则重新生成代理实例
	// if(providerManagerInfo.isSinglable())
	// {
	// synProvider = (Provider) providers.get(
	// syncacheKey);
	//
	// if (synProvider == null) {
	// Provider finalsynProvider = provider;
	// synProvider = (Provider) createInf(providerManagerInfo,
	// finalsynProvider);
	// if (synProvider != null) {
	// providers.put(
	// syncacheKey, synProvider);
	// }
	// }
	// }
	// else
	// {
	// Provider finalsynProvider = provider;
	// synProvider = (Provider) createInf(providerManagerInfo,
	// finalsynProvider);
	//
	// }
	// return synProvider;
	// }
	// else
	// {
	// return provider;
	// }
	// }

	/**
	 * 获取管理服务的特定提供者实例对象
	 */
	public Object getProvider(String providerManagerType, String sourceType)
			throws SPIException {
		return getProvider((CallContext) null, providerManagerType, sourceType,
				false);
	}

	private final Map<String, Object> servicProviders = new HashMap<String, Object>();

	private final List<DestroyMethod> destroyServiceMethods = new ArrayList<DestroyMethod>();

	private final List<DisposableBean> singleDestorys = new ArrayList<DisposableBean>();

	public Object getProvider(CallContext parent, String providerManagerType,
			String sourceType) throws SPIException {
		return getProvider(parent, providerManagerType, sourceType, false);
	}

	Object getProvider(CallContext callContext, String providerManagerType,
			String sourceType, boolean frombeanobject) throws SPIException {

		int idx = providerManagerType.indexOf("?");

		String _name = providerManagerType;
		if (callContext == null)
			callContext = new CallContext(this);
		if (idx > 0) {
			String params = providerManagerType.substring(idx + 1);
			callContext = buildCallContext(params, callContext);
			providerManagerType = providerManagerType.substring(0, idx);
		}
		ServiceID serviceID = buildServiceID(serviceids, providerManagerType,
				ServiceID.PROVIDER_BEAN_SERVICE, sourceType, this);
//		serviceID.setApplicationContext(this.configfile);
		// if(callContext != null && callContext.getSecutiryContext() != null)
		// callContext.getSecutiryContext().setServiceid(serviceID.getService());
		// new ServiceID(providerManagerType,sourceType,GroupRequest.GET_ALL,0,
		// ServiceID.result_rsplist,ServiceID.PROVIDER_BEAN_SERVICE);
		// ServiceID(String serviceID,int resultMode,int waittime,int
		// resultType)

		ProviderManagerInfo providerManagerInfo = null;
		if (providerManagerType != null) {
			providerManagerInfo = this.providerManager
					.getProviderManagerInfo(serviceID.getService());
		} else {
			providerManagerInfo = this.providerManager
					.getDefaultProviderManagerInfo();
			providerManagerType = providerManagerInfo.getId();
		}
		if (providerManagerInfo == null) {
			if (frombeanobject)
				throw new SPIException("容器["+this.getConfigfile()+"]，SPI[" + providerManagerType + "] in "
						+ callContext.getLoopContext() + " does not exist.");
			else {
				return getBeanObject(callContext, _name, null, true);
			}
		}
		// 各spi管理者的缓冲关键字为providerManagerType ＋ ":" + sourceType;
		if (sourceType == null || sourceType.equals("")) {
			sourceType = providerManagerInfo.getDefaulProviderInfo().getType();
		}

		String key = providerManagerType + ":" + sourceType;
		Object finalsynProvider = null;
		if (providerManagerInfo.isSinglable()) {
			finalsynProvider = servicProviders.get(key);
			if (finalsynProvider != null)
				return finalsynProvider;
		}

		Object provider = null;

		provider = providerManagerInfo
				.getSecurityProviderInfoByType(sourceType).getProvider(
						callContext);
		if (provider == null)
			throw new SPIException("容器["+this.getConfigfile()+"]，管理服务[" + key + "]为null,请检查相关配置是否正确。");

		finalsynProvider = provider;

		// 生成管理服务的动态代理实例,如果不是以下的情况则不需要创建代理
		try {
			if (providerManagerInfo.enableTransaction()
					&& providerManagerInfo.isSynchronizedEnabled()) {
				if (providerManagerInfo.getProviderInfoQueue().size() > 1)
					finalsynProvider = createSynTXInf(callContext,
							providerManagerInfo, finalsynProvider, serviceID);
				else {
					finalsynProvider = createTXInf(callContext,
							providerManagerInfo, finalsynProvider, serviceID);
				}

			} else if (providerManagerInfo.enableTransaction()) {
				finalsynProvider = createTXInf(callContext,
						providerManagerInfo, finalsynProvider, serviceID);
			} else if (providerManagerInfo.isSynchronizedEnabled()) {
				if (providerManagerInfo.getProviderInfoQueue().size() > 1) {
					finalsynProvider = createSynInf(callContext,
							providerManagerInfo, finalsynProvider, serviceID);
				} else if (providerManagerInfo.usedCustomInterceptor()) {
					finalsynProvider = createInf(callContext,
							providerManagerInfo, finalsynProvider, serviceID);
				}

			} else if (providerManagerInfo.usedCustomInterceptor() || providerManagerInfo.enableAsyncCall()) {
				finalsynProvider = createInf(callContext, providerManagerInfo,
						finalsynProvider, serviceID);
			} else if (serviceID.isRemote()) {
				finalsynProvider = createInf(callContext, providerManagerInfo,
						finalsynProvider, serviceID);
			}

		} catch (Exception e) {
			e.printStackTrace();
			throw new SPIException(e);

		}
		if (providerManagerInfo.isSinglable()
				&& (providerManagerInfo.enableTransaction()
						|| providerManagerInfo.enableAsyncCall()
						|| providerManagerInfo.isSynchronizedEnabled()
						|| providerManagerInfo.usedCustomInterceptor() || serviceID
						.isRemote())) {
			// if(callContext == null || (callContext != null &&
			// !callContext.containHeaders() && !serviceID.isRestStyle()))
			if (callContext == null
					|| (callContext != null && !serviceID.isRestStyle())) {
				synchronized (servicProviders) {

					Object temp = servicProviders.get(key);
					if (temp != null)
						return temp;
					servicProviders.put(key, finalsynProvider);
				}
			}

		}
		return finalsynProvider;

	}

	// public static void main(String[] args)
	// {
	// Method[] methods = Test.class.getMethods();
	// for(int i = 0; i < methods.length; i ++)
	// {
	// Method m = methods[i];
	// Class[] cluss = m.getParameterTypes();
	// for(int j = 0; j < cluss.length; j ++)
	// {
	//    			
	// }
	// }
	//    	
	// System.out.println(String.class);
	// }

	public static void main(String[] args) {
		long s = System.currentTimeMillis();
		for (int i = 0; i < 600000; i++) {
			System.out.println(i);
		}
		long end = System.currentTimeMillis();
		System.out.println((end - s) + "s");
	}

	public String getProperty(String name) {
		return this.providerManager.getProperty(name);
	}

	public Set<String> getPropertyKeys() {
		return this.providerManager.getPropertyKeys();
	}

	public int getIntProperty(String name) {
		return this.providerManager.getIntProperty(name);
	}
	
	public long getLongProperty(String name) {
		return this.providerManager.getLongProperty(name);
	}
	
	public long getLongProperty(String name,long defaultvalue) {
		return this.providerManager.getLongProperty(name,defaultvalue);
	}

	public boolean getBooleanProperty(String name) {
		return this.providerManager.getBooleanProperty(name);
	}

	public String getProperty(String name, String defaultValue) {
		return this.providerManager.getProperty(name, defaultValue);
	}

	public Object getObjectProperty(String name) {
		return getObjectProperty(name, null);
	}

	public Object getObjectProperty(String name, String defaultValue) {
		return this.providerManager.getObjectProperty(name, defaultValue);
	}

	public int getIntProperty(String name, int defaultValue) {
		return this.providerManager.getIntProperty(name, defaultValue);
	}

	public boolean getBooleanProperty(String name, boolean defaultValue) {
		return this.providerManager.getBooleanProperty(name, defaultValue);
	}

	
	


	public Object getBeanObject(String name) {
		return getBeanObject(name, (Object)null);
		// return this.providerManager.getObjectProperty(name);
		// if(value == null)
		// throw new AssembleException("配置文件没有指定属性[" + name + "]！");

	}
	
	public <T> T getTBeanObject(String name,Class<T> clazz) {
		return (T)getBeanObject(name, (Object)null);
		// return this.providerManager.getObjectProperty(name);
		// if(value == null)
		// throw new AssembleException("配置文件没有指定属性[" + name + "]！");

	}

	public Object getBeanObject(String name, Object defaultValue) {
		return getBeanObject(null, name, defaultValue, false);
		// return this.providerManager.getObjectProperty(name,
		// defaultValue);
		// if(value == null)
		// throw new AssembleException("配置文件没有指定属性[" + name + "]！");

	}
	
	public <T> T getTBeanObject(String name, T defaultValue,Class<T> clazz) {
		return getTBeanObject((CallContext)null,name,defaultValue,clazz);
		// return this.providerManager.getObjectProperty(name,
		// defaultValue);
		// if(value == null)
		// throw new AssembleException("配置文件没有指定属性[" + name + "]！");

	}

	public ProSet getSetProperty(String name) {
		return this.providerManager.getSetProperty(name);
		// if(value == null)
		// throw new AssembleException("配置文件没有指定属性[" + name + "]！");

	}

	public ProSet getSetProperty(String name, ProSet defaultValue) {
		return this.providerManager.getSetProperty(name, defaultValue);

	}

	public ProList getListProperty(String name) {
		return this.providerManager.getListProperty(name);

	}

	public ProList getListProperty(String name, ProList defaultValue) {
		return this.providerManager.getListProperty(name, defaultValue);

	}

	public ProMap getMapProperty(String name) {
		return this.providerManager.getMapProperty(name);
		// if(value == null)
		// throw new AssembleException("配置文件没有指定属性[" + name + "]！");

	}

	public ProMap getMapProperty(String name, ProMap defaultValue) {
		return this.providerManager.getMapProperty(name, defaultValue);

	}
	
	public ProArray getArrayProperty(String name) {
		return this.providerManager.getArrayProperty(name);
		// if(value == null)
		// throw new AssembleException("配置文件没有指定属性[" + name + "]！");

	}

	public ProArray getProArrayProperty(String name, ProArray defaultValue) {
		return this.providerManager.getArrayProperty(name, defaultValue);

	}


	

	// /**
	// * 这个方法需要对服务事务进行相应的处理
	// * @param context
	// * @param name
	// * @param defaultValue
	// * @return
	// */
	// @SuppressWarnings("unchecked")
	// public static Object getBeanObject(Context context, String name,Object
	// defaultValue)
	// {
	// ServiceID serviceID = new
	// ServiceID(name,GroupRequest.GET_ALL,0,ServiceID.
	// result_rsplist,ServiceID.PROPERTY_BEAN_SERVICE);
	// Pro providerManagerInfo =
	// this.providerManager.getPropertyBean
	// (serviceID.getService());
	// if(providerManagerInfo == null)
	// {
	// ProviderManagerInfo providerManagerInfo_ = null;
	//            
	// providerManagerInfo_ = this.providerManager
	// .getProviderManagerInfo(serviceID.getService());
	// if(providerManagerInfo_ == null)
	// throw new SPIException("没有定义名称为[" + name + "]的bean对象。");
	// return BaseSPIManager.getProvider(context, name, null);
	// }
	//        
	// String key = name;
	// Object finalsynProvider = null;
	// if (providerManagerInfo.isSinglable()) {
	// finalsynProvider = servicProviders.get(key);
	// if (finalsynProvider != null)
	// return finalsynProvider;
	// }
	// finalsynProvider =
	// this.providerManager.getBeanObject(context
	// ,serviceID.getService(),defaultValue);
	// // ServiceID serviceID = new
	// ServiceID(name,GroupRequest.GET_ALL,0,ServiceID
	// .result_rsplist,ServiceID.PROPERTY_BEAN_SERVICE);
	// if(providerManagerInfo.enableTransaction() || serviceID.isRemote())
	// {
	//            
	// finalsynProvider = createInf(context, providerManagerInfo,
	// finalsynProvider,serviceID);
	// if (providerManagerInfo.isSinglable()) {
	// synchronized (servicProviders) {
	// servicProviders.put(key, finalsynProvider);
	// }
	// }
	// }
	// return finalsynProvider;
	// }

	public Object getBeanObject(CallContext context, String name,
			Object defaultValue) {
		return getBeanObject(context, name, defaultValue, false);
	}

	/**
	 * 构建请求应用调用上下文中参数头信息和安全上下文信息
	 * 
	 * @fixed biaoping.yin 2010-10-11
	 * @param params
	 * @param context
	 * @return
	 */
	public CallContext buildCallContext(String params, CallContext context) {
		// if (context == null)
		// context = new CallContext(this);
		// StringTokenizer tokenizer = new StringTokenizer(params, "&", false);
		//
		// /**
		// * 协议中包含的属性参数，可以用来做路由条件
		// */
		// Headers headers = null;
		// SecurityContext securityContext = null;
		// String user = null;
		// String password = null;
		// while (tokenizer.hasMoreTokens()) {
		//
		// String parameter = tokenizer.nextToken();
		//
		// int idex = parameter.indexOf("=");
		// if (idex <= 0) {
		// throw new SPIException("非法的服务参数串[" + params + "]");
		// }
		// StringTokenizer ptokenizer = new StringTokenizer(parameter, "=",
		// false);
		// String name = ptokenizer.nextToken();
		// String value = ptokenizer.nextToken();
		// Header header = new Header(name, value);
		// if (name.equals(SecurityManager.USER_ACCOUNT_KEY)) {
		// user = value;
		//
		// } else if (name.equals(SecurityManager.USER_PASSWORD_KEY)) {
		// password = value;
		//
		// } else {
		// if (headers == null)
		// headers = new Headers();
		// headers.put(header.getName(), header);
		// }
		// }
		// if (securityContext == null)
		// securityContext = new SecurityContext(user, password);
		// context.setSecutiryContext(securityContext);
		// context.setHeaders(headers);
		// return context;
		return buildCallContext(params, context, this);
	}

	/**
	 * 构建特定组件管理容器远程请求调用上下文中参数头信息和安全上下文信息
	 * 
	 * @fixed biaoping.yin 2010-10-11
	 * @param params
	 * @param context
	 * @param applicationContext
	 * @return
	 */
	public static CallContext buildCallContext(String params,
			CallContext context, BaseApplicationContext applicationContext) {
		if (context == null) {
			context = new CallContext(applicationContext);
		}
		StringTokenizer tokenizer = new StringTokenizer(params, "&", false);

		/**
		 * 协议中包含的属性参数，可以用来做路由条件
		 */
		Headers headers = null;
		SecurityContext securityContext = null;
		String user = null;
		String password = null;
		while (tokenizer.hasMoreTokens()) {

			String parameter = tokenizer.nextToken();

			int idex = parameter.indexOf("=");
			if (idex <= 0) {
				throw new SPIException("非法的服务参数串[" + params + "]");
			}
			StringTokenizer ptokenizer = new StringTokenizer(parameter, "=",
					false);
			String name = ptokenizer.nextToken();
			String value = ptokenizer.nextToken();
			Header header = new Header(name, value);
			if (name.equals(SecurityManager.USER_ACCOUNT_KEY)) {
				user = value;

			} else if (name.equals(SecurityManager.USER_PASSWORD_KEY)) {
				password = value;

			} else {
				if (headers == null)
					headers = new Headers();
				headers.put(header.getName(), header);
			}
		}
		if (securityContext == null)
			securityContext = new SecurityContext(user, password);
		context.setSecutiryContext(securityContext);
		context.setHeaders(headers);
		return context;
	}
	
	
	/**
	 * 构建特定组件管理容器远程请求调用上下文中参数头信息和安全上下文信息
	 * 
	 * @fixed biaoping.yin 2010-10-11
	 * @param params
	 * @param context
	 * @param applicationContext
	 * @return
	 */
	public static CallContext buildClientCallContext(String params,
			CallContext context) {
		if(params == null || params.equals(""))
			return context;
		StringTokenizer tokenizer = new StringTokenizer(params, "&", false);
		
		/**
		 * 协议中包含的属性参数，可以用来做路由条件
		 */
		Headers headers = null;
		SecurityContext securityContext = null;
		String user = null;
		String password = null;
		while (tokenizer.hasMoreTokens()) {

			String parameter = tokenizer.nextToken();

			int idex = parameter.indexOf("=");
			if (idex <= 0) {
				throw new SPIException("非法的服务参数串[" + params + "]");
			}
			StringTokenizer ptokenizer = new StringTokenizer(parameter, "=",
					false);
			String name = ptokenizer.nextToken();
			String value = ptokenizer.nextToken();
			Header header = new Header(name, value);
			if (name.equals(SecurityManager.USER_ACCOUNT_KEY)) {
				user = value;

			} else if (name.equals(SecurityManager.USER_PASSWORD_KEY)) {
				password = value;

			} else {
				if (headers == null)
					headers = new Headers();
				headers.put(header.getName(), header);
			}
		}
		if (securityContext == null)
			securityContext = new SecurityContext(user, password);
		context.setSecutiryContext(securityContext);
		context.setHeaders(headers);
		return context;
	}
	
	
	/**
	 * bean工厂方法
	 * 
	 * @param context
	 * @param name
	 * @param defaultValue
	 * @return
	 */
	@SuppressWarnings("unchecked")
	public Object getBeanObjectFromRefID(CallContext context, RefID name,String strrefid ,Object defaultValue) {
//		// 分析服务参数
//		int idx = name.indexOf("?");
//
//		String _name = name;
		if (context == null)
			context = new CallContext(this);

		Pro providerManagerInfo = this.providerManager
				.getInnerPropertyBean(name,strrefid);

		if (providerManagerInfo == null) {
			if(defaultValue == null)
				throw new SPIException("容器["+this.getConfigfile()+"]没有定义名称为[" + strrefid + "]的bean对象。");
			else
				return defaultValue;
		}
		Object finalsynProvider =  providerManagerInfo.getTrueValue(context,
				defaultValue);
		return this.proxyObject(providerManagerInfo, finalsynProvider, providerManagerInfo.getXpath());
		
	}

	/**
	 * bean工厂方法
	 * 
	 * @param context
	 * @param name
	 * @param defaultValue
	 * @return
	 */
	@SuppressWarnings("unchecked")
	protected Object getBeanObject(CallContext context, String name, Object defaultValue,
			boolean fromprovider) {
		// 分析服务参数
		int idx = name.indexOf("?");

		String _name = name;
		if (context == null)
			context = new CallContext(this);
		if (idx > 0) {
			String params = name.substring(idx + 1);
			context = buildCallContext(params, context);
			// name = name.substring(0,idx);
		}

		ServiceID serviceID = buildServiceID(serviceids, name,
				ServiceID.PROPERTY_BEAN_SERVICE, this.configfile, this);
//		serviceID.setApplicationContext(this.configfile);
		// new ServiceID(name,GroupRequest.GET_ALL,0,ServiceID.result_rsplist,
		// ServiceID.PROPERTY_BEAN_SERVICE);
		Pro providerManagerInfo = this.providerManager
				.getPropertyBean(serviceID.getService());
		// if(context != null && context.getSecutiryContext() != null)
		// context.getSecutiryContext().setServiceid(serviceID.getService());
		if (providerManagerInfo == null) {
			if (!fromprovider) {
				ProviderManagerInfo providerManagerInfo_ = null;

				providerManagerInfo_ = this.providerManager
						.getProviderManagerInfo(serviceID.getService());
				if (providerManagerInfo_ == null)
				{
					if(defaultValue == null)
						throw new SPIException("容器["+this.getConfigfile()+"]没有定义名称为[" + name + "]的bean对象。");
					else
						return defaultValue;
				}
				return getProvider(context, _name, null, true);
			} else {
				if(defaultValue == null)
					throw new SPIException("容器["+this.getConfigfile()+"]没有定义名称为[" + name + "]的bean对象。");
				else
					return defaultValue;
			}
		}
		return getBeanObject(context, providerManagerInfo, defaultValue,
				serviceID);
		
	}
	
	
	/**
	 * bean工厂方法
	 * 
	 * @param context
	 * @param name
	 * @param defaultValue
	 * @return
	 */
	@SuppressWarnings("unchecked")
	protected <T> T getTBeanObject(CallContext context, String name,T defaultValue,Class<T> interfaceclazz) {
		// 分析服务参数
		int idx = name.indexOf("?");

		String _name = name;
		if (context == null)
			context = new CallContext(this);
		if (idx > 0) {
			String params = name.substring(idx + 1);
			context = buildCallContext(params, context);
			// name = name.substring(0,idx);
		}

		ServiceID serviceID = buildServiceID(serviceids, name,
				ServiceID.PROPERTY_BEAN_SERVICE, this.configfile, this);
		
//		serviceID.setApplicationContext(this.configfile);
		// new ServiceID(name,GroupRequest.GET_ALL,0,ServiceID.result_rsplist,
		// ServiceID.PROPERTY_BEAN_SERVICE);
		Pro providerManagerInfo = this.providerManager
				.getPropertyBean(serviceID.getService());
		// if(context != null && context.getSecutiryContext() != null)
		// context.getSecutiryContext().setServiceid(serviceID.getService());
		if (providerManagerInfo == null) {
//			if (!fromprovider) {
//				ProviderManagerInfo providerManagerInfo_ = null;
//
//				providerManagerInfo_ = this.providerManager
//						.getProviderManagerInfo(serviceID.getService());
//				if (providerManagerInfo_ == null)
//					throw new SPIException("没有定义名称为[" + name + "]的bean对象。");
//				return getProvider(context, _name, null, true);
//			} else 
			{
				throw new SPIException("容器["+this.getConfigfile()+"]没有定义名称为[" + name + "]的bean对象。");
			}
		}
		return (T)getBeanObject(context, providerManagerInfo, null,
				serviceID);
		
	}
	
	


	public Object getBeanObject(CallContext context, String name) {
		return getBeanObject(context, name, null, false);
	}

	public Pro getProBean(String name) {
		// TODO Auto-generated method stub
		return this.providerManager.getPropertyBean(name);
	}

	public Object getBeanObject(CallContext context, Pro providerManagerInfo) {
		return getBeanObject(context, providerManagerInfo, null, null);
	}

	/**
	 * bean组件工厂方法， 如果serviceID不为空，则serviceID是根据getBeanObject(Context context,
	 * String name,Object defaultValue)方法的name生成的
	 * 否则需要根据providerManagerInfo的name或者refid来生成serviceID
	 * 
	 * @param context
	 * @param providerManagerInfo
	 * @param defaultValue
	 * @param serviceID
	 * @return
	 */
	public Object getBeanObject(CallContext context, Pro providerManagerInfo,
			Object defaultValue, ServiceID serviceID) {
		if (providerManagerInfo == null)
			throw new SPIException("容器["+this.getConfigfile()+"]bean对象为空。");
		String key = providerManagerInfo.getName();
		if (providerManagerInfo.isRefereced()) {
			Object retvalue = providerManagerInfo.getTrueValue(context,
					defaultValue);
			return retvalue;
		}

		Object finalsynProvider = null;
		if (serviceID == null) {
			serviceID = buildServiceID(serviceids, key,
					ServiceID.PROPERTY_BEAN_SERVICE, this);
			// serviceID.setApplicationContext(this.configfile);
		}
		// new ServiceID(key, GroupRequest.GET_ALL
		// ,0,ServiceID.result_rsplist,ServiceID.PROPERTY_BEAN_SERVICE);
		key = serviceID.getOrigineServiceID();
		finalsynProvider = this.providerManager.getBeanObject(context,
				providerManagerInfo, defaultValue);
		if (providerManagerInfo.enableTransaction()
				|| providerManagerInfo.enableAsyncCall()
				|| providerManagerInfo.usedCustomInterceptor()
				|| serviceID.isRemote() ) {
			if (providerManagerInfo.isSinglable()) {
				// String key = serviceID.getServiceID();
				// if(context != null && !context.containHeaders() &&
				// !serviceID.isRestStyle()
				// )//如果包含头信息时，代理类将不能被缓冲，原因是头信息的动态性会导致缓冲实例过多
				if (context != null && !serviceID.isRestStyle())// 如果包含头信息时，代理类将不能被缓冲，原因是头信息的动态性会导致缓冲实例过多
				{
					Object provider = servicProviders.get(key);
					if (provider != null)
						return provider;
					synchronized (servicProviders) {
						provider = servicProviders.get(key);
						if (provider != null)
							return provider;
						provider = createInf(context, providerManagerInfo,
								finalsynProvider, serviceID);
						servicProviders.put(key, provider);
					}
					return provider;
				} else {
					finalsynProvider = createInf(context, providerManagerInfo,
							finalsynProvider, serviceID);
					return finalsynProvider;
				}

			} else {
				finalsynProvider = createInf(context, providerManagerInfo,
						finalsynProvider, serviceID);
				return finalsynProvider;
			}
		} else {
			return finalsynProvider;
		}
	}
	
	
	/**
	 * bean组件工厂方法， 如果serviceID不为空，则serviceID是根据getBeanObject(Context context,
	 * String name,Object defaultValue)方法的name生成的
	 * 否则需要根据providerManagerInfo的name或者refid来生成serviceID
	 * 
	 * @param context
	 * @param providerManagerInfo
	 * @param defaultValue
	 * @param serviceID
	 * @return
	 */
	protected Object getBeanObjectFromRefID(CallContext context, Pro providerManagerInfo,
			Object defaultValue) {
		if (providerManagerInfo == null)
			throw new SPIException("容器["+this.getConfigfile()+"]bean对象为空。");
		
		if (providerManagerInfo.isRefereced()) {
			Object retvalue = providerManagerInfo.getTrueValue(context,
					defaultValue);
			return retvalue;
		}

		Object finalsynProvider = null;
		
		// new ServiceID(key, GroupRequest.GET_ALL
		// ,0,ServiceID.result_rsplist,ServiceID.PROPERTY_BEAN_SERVICE);
		finalsynProvider = this.providerManager.getBeanObject(context,
				providerManagerInfo, defaultValue);
		return this.proxyObject(providerManagerInfo, finalsynProvider, providerManagerInfo.getXpath());
		
	}

	public String getStringExtendAttribute(String name, String extendName) {
		Pro pro = getProBean(name);
		if (pro == null)
			return null;
		// TODO Auto-generated method stub
		return pro.getStringExtendAttribute(extendName);
	}

	public Object getExtendAttribute(String name, String extendName) {
		Pro pro = getProBean(name);
		if (pro == null)
			return null;
		return pro.getExtendAttribute(extendName);

	}

	public int getIntExtendAttribute(String name, String extendName) {
		Pro pro = getProBean(name);
		if (pro == null)
			return -1;
		return pro.getIntExtendAttribute(extendName);

	}

	public long getLongExtendAttribute(String name, String extendName) {
		// TODO Auto-generated method stub
		Pro pro = getProBean(name);
		if (pro == null)
			return -1;
		return pro.getLongExtendAttribute(extendName);
	}

	public boolean getBooleanExtendAttribute(String name, String extendName) {
		// TODO Auto-generated method stub
		Pro pro = getProBean(name);
		if (pro == null)
			return false;
		return pro.getBooleanExtendAttribute(extendName);
	}

	/**
	 * 返回一级配置文件清单
	 * 
	 * @return
	 */
	public List getTraceFiles() {
		return this.providerManager.getTraceFiles();
	}

	/**
	 * 返回给定标识的管理服务配置文件信息
	 * 
	 * @param id
	 * @return
	 */
	public LinkConfigFile getLinkConfigFile(String id) {
		return this.providerManager.getLinkConfigFile(id);
	}

	public Map getManagers() {
		return this.providerManager.getManagers();
	}

	public ServiceProviderManager getServiceProviderManager() {
		return this.providerManager;
	}

	public String getConfigfile() {
		return configfile;
	}

	public static String getRealPath(String parent, String file) {
		StringBuffer ret = new StringBuffer();

		if (parent.endsWith("/")) {

			if (!file.startsWith("/"))
				ret.append(parent).append(file);
			else
				ret.append(parent).append(file.substring(1));
			return ret.toString();
		} else {
			if (!file.startsWith("/"))
				ret.append(parent).append("/").append(file);
			else
				ret.append(parent).append(file);
			return ret.toString();
		}
	}

	public boolean isSingleton(String beanname) {
		Pro pro = this.getProBean(beanname);
		Assert.notNull(pro);
		return pro.isSinglable();

	}

	// ---------------------------------------------------------------------
	// Implementation of MessageSource interface
	// ---------------------------------------------------------------------

	public String getMessage(String code, Object args[], String defaultMessage,
			Locale locale) {
		return getMessageSource()
				.getMessage(code, args, defaultMessage, locale);
	}

	public String getMessage(String code, String defaultMessage, Locale locale) {
		return getMessageSource()
				.getMessage(code, null, defaultMessage, locale);
	}

	public String getMessage(String code,Locale locale) {
		return getMessageSource().getMessage(code, null, null, locale);
	}
	public String getMessage(String code, String defaultMessage) {
		return getMessageSource().getMessage(code, null, defaultMessage, null);
	}

	public String getMessage(String code) {
		return getMessageSource().getMessage(code, null, null, null);
	}

	public String getMessage(String code, Object args[], Locale locale)
			throws NoSuchMessageException {
		return getMessageSource().getMessage(code, args, locale);
	}

	public String getMessage(MessageSourceResolvable resolvable, Locale locale)
			throws NoSuchMessageException {
		return getMessageSource().getMessage(resolvable, locale);
	}

	/**
	 * Return the internal MessageSource used by the context.
	 * 
	 * @return the internal MessageSource (never <code>null</code>)
	 * @throws IllegalStateException
	 *             if the context has not been initialized yet
	 */
	private MessageSource getMessageSource() throws IllegalStateException {
		if (this.messageSource == null) {
			throw new IllegalStateException(
					"MessageSource not initialized - "
							+ "call 'refresh' before accessing messages via the context: "
							+ this);
		}
		return this.messageSource;
	}

	private ResourceLoader resourceLoader;

	/**
	 * This implementation delegates to this context's ResourceLoader if it
	 * implements the ResourcePatternResolver interface, falling back to the
	 * default superclass behavior else.
	 * 
	 * @see #setResourceLoader
	 */
	public Resource[] getResources(String locationPattern) throws IOException {
		if (this.resourceLoader instanceof ResourcePatternResolver) {
			return ((ResourcePatternResolver) this.resourceLoader)
					.getResources(locationPattern);
		}
		return this.resourcePatternResolver.getResources(locationPattern);
	}

	/**
	 * Return the ResourcePatternResolver to use for resolving location patterns
	 * into Resource instances. Default is a

	 * , supporting Ant-style location patterns.
	 * <p>
	 * Can be overridden in subclasses, for extended resolution strategies, for
	 * example in a web environment.
	 * <p>
	 * <b>Do not call this when needing to resolve a location pattern.</b> Call
	 * the context's <code>getResources</code> method instead, which will
	 * delegate to the ResourcePatternResolver.
	 * 
	 * @return the ResourcePatternResolver for this context
	 * @see #getResources

	 */
	protected ResourcePatternResolver getResourcePatternResolver() {
		return new PathMatchingResourcePatternResolver(this);
	}

	/**
	 * This implementation delegates to this context's ResourceLoader if set,
	 * falling back to the default superclass behavior else.
	 * 
	 * @see #setResourceLoader
	 */
	public Resource getResource(String location) {
		if (this.resourceLoader != null) {
			return this.resourceLoader.getResource(location);
		}
		return super.getResource(location);
	}

	/**
	 * Set a ResourceLoader to use for this context. If set, the context will
	 * delegate all <code>getResource</code> calls to the given ResourceLoader.
	 * If not set, default resource loading will apply.
	 * <p>
	 * The main reason to specify a custom ResourceLoader is to resolve resource
	 * paths (withour URL prefix) in a specific fashion. The default behavior is
	 * to resolve such paths as class path locations. To resolve resource paths
	 * as file system locations, specify a FileSystemResourceLoader here.
	 * <p>
	 * You can also pass in a full ResourcePatternResolver, which will be
	 * autodetected by the context and used for <code>getResources</code> calls
	 * as well. Else, default resource pattern matching will apply.
	 * 
	 * @see #getResource
	 * @see DefaultResourceLoader

	 * @see #getResources
	 */
	public void setResourceLoader(ResourceLoader resourceLoader) {
		this.resourceLoader = resourceLoader;
	}

	public boolean containsBean(String themeSourceBeanName) {
		return this.providerManager.containsBean(themeSourceBeanName);

	}

	public Object createBean(Class clazz) throws BeanInstanceException {

		return createBean(clazz, null);

	}
	
	public Object createBean(String clazz) throws BeanInstanceException {

		Class clss = null;
		try {
			clss = Class.forName(clazz);
		} catch (ClassNotFoundException e) {
			throw new BeanInstanceException(e);
		}
		return createBean(clss, null);

	}
	
	

	public Object createBean(Class clazz, BeanInf providerManagerInfo)
			throws BeanInstanceException {

		try {
			Object ret = clazz.newInstance();

			return initBean(ret, providerManagerInfo);
		} catch (InstantiationException e) {
			throw new BeanInstanceException(e);
		} catch (IllegalAccessException e) {
			throw new BeanInstanceException(e);
		}

		catch (BeanInstanceException e) {
			throw e;
		} catch (Exception e) {
			throw new BeanInstanceException(e);
		}

	}

	public Object initBean(Object bean, BeanInf providerManagerInfo)
			throws BeanInstanceException {

		try {

			BeanAccembleHelper.initBean(bean, providerManagerInfo, this);
			return bean;
		}

		catch (BeanInstanceException e) {
			throw e;
		} catch (Exception e) {
			throw new BeanInstanceException(e);
		}

	}

	public Object initBean(Object bean, String beanName)
			throws BeanInstanceException {

		try {

			BeanAccembleHelper.initBean(bean, beanName, this);
			return bean;
		}

		catch (BeanInstanceException e) {
			throw e;
		} catch (Exception e) {
			throw new BeanInstanceException(e);
		}

	}

	/** ResourcePatternResolver used by this context */
	private ResourcePatternResolver resourcePatternResolver;

	/** MessageSource we delegate our implementation of this interface to */
	protected MessageSource messageSource;

	// private static final Logger log =
	// Logger.getLogger(ApplicationContext.class);
	/**
	 * Initialize the MessageSource. Use parent's if none defined in this
	 * context.
	 */
	protected void initMessageSource() {

//		if (this.containsBean(MESSAGE_SOURCE_BEAN_NAME)) {
//			this.messageSource = (MessageSource) this
//					.getBeanObject(MESSAGE_SOURCE_BEAN_NAME);
//
//			if (log.isDebugEnabled()) {
//				log.debug("Using MessageSource [" + this.messageSource + "]");
//			}
//		} 
//		else {
//			// Use empty MessageSource to be able to accept getMessage calls.
//			DelegatingMessageSource dms = new DelegatingMessageSource();
//			dms.setParentMessageSource(getInternalParentMessageSource());
//			this.messageSource = dms;
//			// beanFactory.registerSingleton(MESSAGE_SOURCE_BEAN_NAME,
//			// this.messageSource);
//			if (log.isDebugEnabled()) {
//				log.debug("Unable to locate MessageSource with name '"
//						+ MESSAGE_SOURCE_BEAN_NAME + "': using default ["
//						+ this.messageSource + "]");
//			}
//		}
		
		// Use empty MessageSource to be able to accept getMessage calls.
		DelegatingMessageSource dms = new DelegatingMessageSource();
		dms.setParentMessageSource(getInternalParentMessageSource());
		this.messageSource = dms;
		// beanFactory.registerSingleton(MESSAGE_SOURCE_BEAN_NAME,
		// this.messageSource);
		if (log.isDebugEnabled()) {
			log.debug("Unable to locate MessageSource with name '"
					+ MESSAGE_SOURCE_BEAN_NAME + "': using default ["
					+ this.messageSource.getClass().getCanonicalName() + "]");
		}
	}

	public static final String DEFAULT_MESSAGE_BASENAME = "messages";


//	protected MessageSource getInternalParentMessageSource() {
//		ResourceBundleMessageSource messagesource = new ResourceBundleMessageSource();
//		if(this.configfile != null && !configfile.equals(""))
//		{
//			int index = configfile.lastIndexOf("/");
//			if(index > 0)
//			{
//				String parent = configfile.substring(0,index);
//				messagesource.setBasename(getRealPath(parent, DEFAULT_MESSAGE_BASENAME));
//				
//			}
//			else
//			{
//				messagesource.setBasename(DEFAULT_MESSAGE_BASENAME);
//			}
//		}
//		else
//		{
//			messagesource.setBasename(DEFAULT_MESSAGE_BASENAME);
//		}
//		messagesource.setBundleClassLoader(getClassLoader());
//		messagesource.setUseCodeAsDefaultMessage(true);
//		return messagesource;
//	}
	
	protected MessageSource getInternalParentMessageSource() {
		HotDeployResourceBundleMessageSource messagesource = new HotDeployResourceBundleMessageSource();
		if(this.configfile != null && !configfile.equals(""))
		{
			int index = configfile.lastIndexOf("/");
			if(index > 0)
			{
				String parent = configfile.substring(0,index);
				messagesource.setBasename(getRealPath(parent, DEFAULT_MESSAGE_BASENAME));
				
			}
			else
			{
				messagesource.setBasename(DEFAULT_MESSAGE_BASENAME);
			}
		}
		else
		{
			messagesource.setBasename(DEFAULT_MESSAGE_BASENAME);
		}
		messagesource.setBundleClassLoader(getClassLoader());
		messagesource.setUseCodeAsDefaultMessage(true);
		return messagesource;
	}

	public String[] getStringArray(String key) {
		return this.providerManager.getStringArray(key);
	}
	
	public String[] getStringArray(String key,String[] defaultValues) {
		return this.providerManager.getStringArray(key,defaultValues);
	}
	
	// private static Map<String, ServiceID> serviceids = new
	// java.util.WeakHashMap<String, ServiceID>();

	public abstract ServiceID buildServiceID(Map<String, ServiceID> serviceids,
			String serviceid, int serviceType, String providertype,
			BaseApplicationContext applicationcontext) ;

	

	public abstract ServiceID buildServiceID(Map<String, ServiceID> serviceids,
			String serviceid, int serviceType,
			BaseApplicationContext applicationcontext) ;

	public abstract ServiceID buildBeanServiceID(Map<String, ServiceID> serviceids,
			String serviceid, BaseApplicationContext applicationcontext) ;
	
	/**
	 * 获取parent对应的属性内部的名称为name的Pro对象
	 * parent的格式：
	 * vvvv^^list#!#cccc^^map#!#dddd^^map
	 * vvvv^^list#!#cccc^^map#!#0^^list
	 * @param parent
	 * @param name
	 * @return
	 */
	public Pro getInnerPro(String parent,String name)
	{
		if(parent == null || parent.equals(""))
			return this.getProBean(name);
		
		String[] nodes = parent.split("\\#\\!\\#");
		Pro pro = null;
		String nodetype = null;
		
		String nodename = null;
		
		String oldnodetype = null;
		
		String oldnodename = null;
		for(int i = 0; i < nodes.length; i ++)
		{
			String nodewithtype = nodes[i];
			String[] nodeinfo = nodewithtype.split("\\^\\^");
			
			if(pro == null)
			{
				
				
				nodetype = nodeinfo[1];
				nodename = nodeinfo[0];
				
				pro = this.getProBean(nodename);
				
				if(i == nodes.length -1)
				{
					//达到最后一个父节点:
					if(nodetype.equals("list"))
					{
						ProList list = pro.getList();
						int position = Integer.parseInt(name);
						return list.getPro(position);
					}
					else if(nodetype.equals("map"))
					{
						ProMap map = pro.getMap();
						
						return map.getPro(name);
					}
					else if(nodetype.equals("array"))
					{
						ProArray array = pro.getArray();
						int position = Integer.parseInt(name);
						return array.getPro(position);
					}
					else if(nodetype.equals("set"))
					{
						ProSet array = pro.getSet();
						int position = Integer.parseInt(name);
						return array.getPro(position);
					}
					else if(nodetype.equals("construction"))
					{
						List constructionList = pro.getConstructorParams();
						int position = Integer.parseInt(name);
						return (Pro)constructionList.get(position);
					}
					else if(nodetype.equals("reference"))
					{
						List referencesList = pro.getReferences();
						int position = Integer.parseInt(name);
						return (Pro)referencesList.get(position);
					}
					throw new NullPointerException(this.getConfigfile() + "中不存在[" + parent+","+name + "]，类型为"+nodetype+"的内部property节点");
						
				}
				
			}
			else 
			{
				oldnodetype = nodetype;
			
				oldnodename = nodename;
				nodetype = nodeinfo[1];
				nodename = nodeinfo[0];
				
				if(oldnodetype.equals("list"))
				{
					ProList list = pro.getList();
					pro = list.getPro(Integer.parseInt(nodename));
				}
				else if(oldnodetype.equals("map"))
				{
					ProMap map = pro.getMap();
					pro = map.getPro(nodename);
					
				}
				else if(oldnodetype.equals("array"))
				{
					ProArray array = pro.getArray();
					int position = Integer.parseInt(nodename);
					pro = array.getPro(position);
				}
				else if(oldnodetype.equals("set"))
				{
					ProSet set = pro.getSet();
					int position = Integer.parseInt(nodename);
					pro = set.getPro(position);
				}
				else if(oldnodetype.equals("reference"))
				{
					List referencesList = pro.getReferences();
					int position = Integer.parseInt(oldnodename);
					pro = (Pro)referencesList.get(position);
				}
				else if(oldnodetype.equals("construction"))
				{
					List referencesList = pro.getConstructorParams();
					int position = Integer.parseInt(oldnodename);
					pro = (Pro)referencesList.get(position);
				}
				else if(nodetype.equals("construction"))
				{
					List constructionList = pro.getConstructorParams();
					int position = Integer.parseInt(nodename);
					pro = (Pro)constructionList.get(position);
				}
				else if(nodetype.equals("reference"))
				{
					List referencesList = pro.getReferences();
					int position = Integer.parseInt(nodename);
					pro = (Pro)referencesList.get(position);
				}
				else 
					throw new NullPointerException(this.getConfigfile() + "中不存在[oldnodetype=" + oldnodetype +
					
					",oldnodename="+oldnodename +
					",nodetype="+nodetype +
					
					",nodename="+nodename  + "]的内部property节点");
				
				
				if(i == nodes.length -1)
				{
					//达到最后一个父节点，从对应的节点中获取给定名称name的pro对象:
					if(nodetype.equals("list"))
					{
						ProList list = pro.getList();
						int position = Integer.parseInt(name);
						return list.getPro(position);
					}
					else if(nodetype.equals("map"))
					{
						ProMap map = pro.getMap();
						
						return map.getPro(name);
					}
					else if(nodetype.equals("array"))
					{
						ProArray array = pro.getArray();
						int position = Integer.parseInt(name);
						return array.getPro(position);
					}
					else if(nodetype.equals("set"))
					{
						ProSet array = pro.getSet();
						int position = Integer.parseInt(name);
						return array.getPro(position);
					}
					else if(nodetype.equals("construction"))
					{
						List constructionList = pro.getConstructorParams();
						int position = Integer.parseInt(name);
						return (Pro)constructionList.get(position);
					}
					else if(nodetype.equals("reference"))
					{
						List referencesList = pro.getReferences();
						int position = Integer.parseInt(name);
						return (Pro)referencesList.get(position);
					}
					throw new NullPointerException(this.getConfigfile() + "中不存在[" + parent+","+name + "]，类型为"+nodetype+"的内部property节点");
						
				}
			}
			
		}
		throw new NullPointerException(this.getConfigfile() + "中不存在[" + parent+","+name + "]，类型为"+nodetype+"的内部property节点");	
			
	}
	

}


