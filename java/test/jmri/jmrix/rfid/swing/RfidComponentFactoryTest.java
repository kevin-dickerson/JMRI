package jmri.jmrix.rfid.swing;

import org.junit.After;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import jmri.jmrix.rfid.RfidSystemConnectionMemo;
import java.awt.GraphicsEnvironment;

/**
 * Tests for RfidComponentFactory class.
 *
 * @author Paul Bender Copyright (C) 2016
 **/

public class RfidComponentFactoryTest {
        
   private RfidSystemConnectionMemo memo = null;

   @Test
   public void MemoConstructorTest(){
      Assert.assertNotNull("RfidComponentFactory constructor",new RfidComponentFactory(memo));
   }

   @Test
   public void getMenu(){
      Assume.assumeFalse(GraphicsEnvironment.isHeadless());
      RfidComponentFactory zcf = new RfidComponentFactory(memo);
      Assert.assertNotNull("Component Factory getMenu method",zcf.getMenu());
   }

   @Test
   public void getMenuDisabled(){
      memo.setDisabled(true);
      RfidComponentFactory zcf = new RfidComponentFactory(memo);
      Assert.assertNull("Disabled Component Factory getMenu method",zcf.getMenu());
   }

   @Before
   public void setUp(){
        apps.tests.Log4JFixture.setUp();
        jmri.util.JUnitUtil.resetInstanceManager();
        jmri.util.JUnitUtil.initDefaultUserMessagePreferences();
        memo = new RfidSystemConnectionMemo();
   }

   @After
   public void tearDown(){
        memo=null;
        apps.tests.Log4JFixture.tearDown();
        jmri.util.JUnitUtil.resetInstanceManager();
   }

}
