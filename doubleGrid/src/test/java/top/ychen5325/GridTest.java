package top.ychen5325;/**
 *
 */

import com.binance.connector.client.pub.CzClient;
import com.binance.connector.client.pub.model.Order;

import org.easymock.EasyMock;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import lombok.SneakyThrows;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * @author yyy
 * @wx ychen5325
 * @email q1416349095@gmail.com
 */
public class GridTest {


	private Grid service = new Grid();

	private Task task;

	private CzClient czClient;

	@SneakyThrows
	@Before
	public void before() {
		czClient = EasyMock.createMock(CzClient.class);
		Field field = Grid.class.getDeclaredField("czClient");
		field.setAccessible(true);
		field.set(service, czClient);
	}

	/**
	 * 持有的双币资产均满足
	 */
	@Test
	public void initTest01() throws Exception {
		Map<String, BigDecimal> userAssetMap = new HashMap<>();
		userAssetMap.put("ETH", BigDecimal.valueOf(1));
		userAssetMap.put("BNB", BigDecimal.valueOf(1));
		userAssetMap.put("USDT", BigDecimal.valueOf(1));

		EasyMock.expect(czClient.listUserAsset()).andReturn(userAssetMap).anyTimes();
		EasyMock.expect(czClient.getAvgPrice(EasyMock.anyString())).andReturn(BigDecimal.valueOf(2000));
		EasyMock.expect(czClient.getAvgPrice(EasyMock.anyString())).andReturn(BigDecimal.valueOf(400));
		EasyMock.replay(czClient);
		task = service.init();
		Assert.assertEquals(task.getInvestQtyA(), new BigDecimal("0.30000000"));
		Assert.assertEquals(task.getInvestQtyB(), new BigDecimal("1.00000000"));
	}

	/**
	 * 持有的双币资产存在缺额且USDT不足
	 */
	@Test
	public void initTest02() throws Exception {
		Map<String, BigDecimal> userAssetMap = new HashMap<>();
		userAssetMap.put("ETH", BigDecimal.valueOf(0.2));
		userAssetMap.put("BNB", BigDecimal.valueOf(1));
		userAssetMap.put("USDT", BigDecimal.valueOf(100));

		EasyMock.expect(czClient.listUserAsset()).andReturn(userAssetMap).anyTimes();
		EasyMock.expect(czClient.getAvgPrice(EasyMock.anyString())).andReturn(BigDecimal.valueOf(2000));
		EasyMock.expect(czClient.getAvgPrice(EasyMock.anyString())).andReturn(BigDecimal.valueOf(400));
		EasyMock.replay(czClient);
		try {
			service.init();
		} catch (RuntimeException ex) {
			Assert.assertEquals(ex.getMessage(), "存在资金缺额、无法启动");
		}
	}


	/**
	 * A资产存在缺额、但幸运的是usdt足够
	 */
	@Test
	public void initTest03() throws Exception {
		Map<String, BigDecimal> userAssetMap = new HashMap<>();
		userAssetMap.put("ETH", BigDecimal.valueOf(0.2));
		userAssetMap.put("BNB", BigDecimal.valueOf(1));
		userAssetMap.put("USDT", BigDecimal.valueOf(300));

		EasyMock.expect(czClient.listUserAsset()).andReturn(userAssetMap).anyTimes();
		EasyMock.expect(czClient.getAvgPrice(EasyMock.anyString())).andReturn(BigDecimal.valueOf(2000));
		EasyMock.expect(czClient.getAvgPrice(EasyMock.anyString())).andReturn(BigDecimal.valueOf(400));
		Order order = new Order();
		order.setStatus("FILLED");
		EasyMock.expect(czClient.createBuyOfMarketOrder(EasyMock.anyString(), EasyMock.anyObject())).andReturn(order);
		EasyMock.replay(czClient);
		task = service.init();
		Assert.assertEquals(task.getInvestQtyA(), new BigDecimal("0.3000"));
		Assert.assertEquals(task.getInvestQtyB(), new BigDecimal("1.0000"));
	}

	/**
	 * A、B资产存在缺额、但幸运的是usdt足够
	 */
	@Test
	public void initTest04() throws Exception {
		Map<String, BigDecimal> userAssetMap = new HashMap<>();
		userAssetMap.put("ETH", BigDecimal.valueOf(0.2));
		userAssetMap.put("BNB", BigDecimal.valueOf(0.8));
		userAssetMap.put("USDT", BigDecimal.valueOf(500));

		EasyMock.expect(czClient.listUserAsset()).andReturn(userAssetMap).anyTimes();
		EasyMock.expect(czClient.getAvgPrice(EasyMock.anyString())).andReturn(BigDecimal.valueOf(2000));
		EasyMock.expect(czClient.getAvgPrice(EasyMock.anyString())).andReturn(BigDecimal.valueOf(400));
		Order order = new Order();
		order.setStatus("FILLED");
		EasyMock.expect(czClient.createBuyOfMarketOrder(EasyMock.anyString(), EasyMock.anyObject())).andReturn(order).times(2);
		EasyMock.replay(czClient);
		task = service.init();
		Assert.assertEquals(task.getInvestQtyA(), new BigDecimal("0.3000"));
		Assert.assertEquals(task.getInvestQtyB(), new BigDecimal("1.0000"));
	}


	/**
	 * 任务执行中、当前处于窄幅震荡、无成交
	 */
	@Test
	public void loopTest01() throws Exception {
		initTest01();
		EasyMock.reset(czClient);
		EasyMock.expect(czClient.getAvgPrice(EasyMock.anyString())).andReturn(BigDecimal.valueOf(2001));
		EasyMock.expect(czClient.getAvgPrice(EasyMock.anyString())).andReturn(BigDecimal.valueOf(401));
		EasyMock.replay(czClient);
		service.loop(task);
	}

	/**
	 * 汇率上涨、满足卖出条件
	 */
	@Test
	public void loopTest02() throws Exception {
		initTest01();
		EasyMock.reset(czClient);
		EasyMock.expect(czClient.getAvgPrice(EasyMock.anyString())).andReturn(BigDecimal.valueOf(2050));
		EasyMock.expect(czClient.getAvgPrice(EasyMock.anyString())).andReturn(BigDecimal.valueOf(404));
		Order sellOrder = new Order();
		sellOrder.setCumQuote(BigDecimal.valueOf(40.4));
		EasyMock.expect(czClient.createSellOfMarketOrder(EasyMock.anyString(), EasyMock.anyObject())).andReturn(sellOrder);

		Order buyOrder = new Order();
		EasyMock.expect(czClient.createBuyOfMarketOrder(EasyMock.anyString(), EasyMock.anyObject())).andReturn(buyOrder);

		EasyMock.replay(czClient);
		service.loop(task);
	}

	/**
	 * 汇率下跌、满足买入条件
	 */
	@Test
	public void loopTest03() throws Exception {
		initTest01();
		EasyMock.reset(czClient);
		EasyMock.expect(czClient.getAvgPrice(EasyMock.anyString())).andReturn(BigDecimal.valueOf(1950));
		EasyMock.expect(czClient.getAvgPrice(EasyMock.anyString())).andReturn(BigDecimal.valueOf(396));
		Order sellOrder = new Order();
		sellOrder.setCumQuote(BigDecimal.valueOf(39.6));
		EasyMock.expect(czClient.createSellOfMarketOrder(EasyMock.anyString(), EasyMock.anyObject())).andReturn(sellOrder);

		Order buyOrder = new Order();
		EasyMock.expect(czClient.createBuyOfMarketOrder(EasyMock.anyString(), EasyMock.anyObject())).andReturn(buyOrder);

		EasyMock.replay(czClient);
		service.loop(task);
	}


	/**
	 * 满足买入｜卖出条件、但不满足开单金额 <10U
	 */
	@Test
	public void loopTest04() throws Exception {
		initTest01();
		EasyMock.reset(czClient);
		EasyMock.expect(czClient.getAvgPrice(EasyMock.anyString())).andReturn(BigDecimal.valueOf(400));
		EasyMock.expect(czClient.getAvgPrice(EasyMock.anyString())).andReturn(BigDecimal.valueOf(100));
		Order sellOrder = new Order();
		sellOrder.setCumQuote(BigDecimal.valueOf(10));
		EasyMock.expect(czClient.createSellOfMarketOrder(EasyMock.anyString(), EasyMock.anyObject())).andReturn(sellOrder);

		Order buyOrder = new Order();
		EasyMock.expect(czClient.createBuyOfMarketOrder(EasyMock.anyString(), EasyMock.anyObject())).andReturn(buyOrder);

		EasyMock.replay(czClient);
		service.loop(task);
	}

	/**
	 * 主币汇率不断上涨、至主币全部抛售、结束任务
	 */
	@Test
	public void loopTest05() throws Exception {
		initTest01();
		EasyMock.reset(czClient);
		BigDecimal priceA = BigDecimal.valueOf(2000);
		BigDecimal priceB = BigDecimal.valueOf(400);
		BigDecimal qtyA = BigDecimal.ONE;
		BigDecimal qtyB = BigDecimal.ONE;
		BigDecimal curTradeRate = BigDecimal.valueOf(5);
		BigDecimal nextSellP = BigDecimal.valueOf(5.05);
		while (qtyA.multiply(priceA).compareTo(BigDecimal.TEN) > 0) {

			EasyMock.expect(czClient.getAvgPrice(EasyMock.anyString())).andReturn(priceA);
			EasyMock.expect(czClient.getAvgPrice(EasyMock.anyString())).andReturn(priceB);
			priceA = priceA.add(BigDecimal.valueOf(50));
			curTradeRate = priceA.divide(priceB, 8, 1);
			if (curTradeRate.compareTo(nextSellP) > -1) {
				Order sellOrder = new Order();
				BigDecimal swapVal = qtyB.multiply(BigDecimal.valueOf(0.1)).multiply(priceB);
				BigDecimal sellQtyA = swapVal.divide(priceA, 8, 1);
				sellOrder.setCumQuote(swapVal);
				if (sellQtyA.compareTo(qtyA) > -1) {
					sellQtyA = qtyA;
				}
				EasyMock.expect(czClient.createSellOfMarketOrder(EasyMock.anyString(), EasyMock.anyObject())).andReturn(sellOrder);
				qtyA = qtyA.subtract(sellQtyA);
				qtyB = qtyB.multiply(BigDecimal.valueOf(1.1));
				nextSellP = curTradeRate.multiply(BigDecimal.valueOf(1.01));
			}
		}
		EasyMock.expect(czClient.createBuyOfMarketOrder(EasyMock.anyString(), EasyMock.anyObject())).andReturn(new Order()).anyTimes();

		EasyMock.replay(czClient);
		service.loop(task);
	}
}
