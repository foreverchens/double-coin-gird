package top.ychen5325;


import com.binance.connector.client.pub.CzClient;
import com.binance.connector.client.pub.model.Order;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

/**
 * @author yyy
 */
@Slf4j
public class Grid {

	private static Properties properties;

	private static CzClient czClient;

	static {
		InputStream resourceAsStream = Grid.class.getResourceAsStream("/application-grid.yaml");
		properties = new Properties();
		try {
			properties.load(resourceAsStream);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		String ak = properties.getProperty("ak");
		String sk = properties.getProperty("sk");
		czClient = new CzClient(ak, sk);
		properties.remove("ak");
		properties.remove("sk");
	}

	@SneakyThrows
	public void loop(Task task) {
		while (true) {

			TimeUnit.MINUTES.sleep(1);

			// 1.获取双币价格计算汇率、检查汇率是否满足买入｜卖出条件
			String symbolA = task.getSymbolA();
			String symbolB = task.getSymbolB();
			BigDecimal priceA = czClient.getAvgPrice(symbolA);
			BigDecimal priceB = czClient.getAvgPrice(symbolB);
			BigDecimal curTradeRate = priceA.divide(priceB, 8, 1);
			log.info("curP : A-{},B-{},curTradeRate:{},oldTradeRate:{}", priceA, priceB, curTradeRate,
					task.getTradeRate());


			if (curTradeRate.compareTo(task.getNextBugP()) < 1) {
				// 2.当前汇率低于下一次买入汇率、执行买入
				/**
				 * 1. 计算需要卖出的资产B数量 sellQtyB = ${qtyB} * ${SwapValBRate}
				 * 2. 市价卖出sellQtyB数量的资产B、获取swapVal
				 * 3. 市价买入价值为 swapVal的资产A
				 * 4. 根据最新汇率和gridRate配置更新下次买入｜卖出的汇率
				 */
				BigDecimal sellQtyB = task.getInvestQtyB().multiply(task.getSwapValRate());
				if ((sellQtyB.multiply(priceB)).compareTo(BigDecimal.valueOf(10)) < 1) {
					log.info("swapVal < 10,curVal:{}", sellQtyB.multiply(priceB));
					continue;
				}
				Order order = czClient.createSellOfMarketOrder(symbolB, sellQtyB);
				BigDecimal swapVal = order.getCumQuote();
				BigDecimal buyQtyA = swapVal.divide(priceA, 8, 1);
				czClient.createBuyOfMarketOrder(symbolA, swapVal);

				task.setInvestQtyA(task.getInvestQtyA().add(swapVal));
				task.setInvestQtyB(task.getInvestQtyB().subtract(swapVal));
				log.info("tradeRate down sellQtyB:{},swapVal:{},buyQtyA:{}", sellQtyB, swapVal, buyQtyA);
			} else if (curTradeRate.compareTo(task.getNextSellP()) > -1) {
				// 3.当前汇率高于下一次卖出汇率、执行卖出
				/**
				 * 1. 计算需要卖出的资产A的价值 swapVal = ${qtyB} * ${priceB} * ${SwapValBRate}
				 * 2. 计算需要卖出的资产A的数量 sellQtyA = swapVal /  ${priceA}
				 * 3. 市价卖出sellQtyA数量的资产A为swapVal、因为是市价、所以可能会略小于上面计算的swapVal
				 * 4. 市价买入价值为 swapVal的资产B
				 * 5. 根据最新汇率和gridRate配置更新下次买入｜卖出的汇率
				 */
				BigDecimal swapVal = task.getInvestQtyB().multiply(priceB).multiply(task.getSwapValRate());
				if (swapVal.compareTo(BigDecimal.valueOf(10)) < 1) {
					log.info("swapVal < 10,curVal:{}", swapVal);
					continue;
				}
				BigDecimal sellQtyA = swapVal.divide(priceA, 8, 1);
				Order order = czClient.createSellOfMarketOrder(symbolA, sellQtyA);
				swapVal = order.getCumQuote();
				BigDecimal buyQtyB = swapVal.divide(priceB, 8, 1);
				czClient.createBuyOfMarketOrder(symbolB, swapVal);

				task.setInvestQtyA(task.getInvestQtyA().subtract(swapVal));
				task.setInvestQtyB(task.getInvestQtyB().add(swapVal));
				log.info("tradeRate up sellQtyA:{},swapVal:{},buyQtyB:{}", sellQtyA, swapVal, buyQtyB);

			} else {
				// 窄幅震荡中。。。。
				continue;
			}
			task.setTradeRate(curTradeRate);
			BigDecimal gridRate = task.getGridRate();
			task.setNextBugP(curTradeRate.multiply((BigDecimal.valueOf(1).subtract(gridRate))));
			task.setNextSellP(curTradeRate.multiply((BigDecimal.valueOf(1).add(gridRate))));
			log.info("nextBuyP:{},nextSellP:{}", task.getNextBugP(), task.getNextSellP());
		}
	}

	public Task init() {
		/**
		 * 检查是否满足启动条件
		 *
		 * 1. 获取用户当前A和B的可用资产
		 * 2. 如果存在缺额、则检查缺额和可用USDT的价值
		 * 3. 使用用户的USDT购买足额的A｜B资产
		 */

		// 如 BNB & ETH
		String assertA = properties.getProperty("assertA");
		String assertB = properties.getProperty("assertB");
		// 如 BNBUSDT & ETHUSDT
		String symbolA = assertA.concat("USDT");
		String symbolB = assertB.concat("USDT");

		// ETH1个 BNB 1个
		Map<String, BigDecimal> userAssetMap = czClient.listUserAsset();
		BigDecimal freeQtyA = userAssetMap.getOrDefault(assertA,BigDecimal.ZERO);
		BigDecimal freeQtyB = userAssetMap.getOrDefault(assertB,BigDecimal.ZERO);

		// ETH 2000U 、BNB 400U
		BigDecimal priceA = czClient.getAvgPrice(symbolA);
		BigDecimal priceB = czClient.getAvgPrice(symbolB);

		// 投资额度、如 ETH初始投资600U、BNB初始投资400U
		BigDecimal investValA = new BigDecimal(properties.getProperty("investValA"));
		BigDecimal investValB = new BigDecimal(properties.getProperty("investValB"));

		// 当前持有价值、如ETH当前持有价值500U、BNB当前持有价值400U
		BigDecimal freeAssertValA = freeQtyA.multiply(priceA);
		BigDecimal freeAssertValB = freeQtyB.multiply(priceB);

		log.info("assert list : {},{} ", assertA, assertB);
		log.info("freeQty : A-{},B-{}", freeQtyA, freeQtyB);
		log.info("cur price : A-{},B-{}", priceA, priceB);
		log.info("investVal : A-{},B-{}", investValA, investValB);
		log.info("freeVal : A-{},B-{}", freeAssertValA, freeAssertValB);

		if (freeAssertValA.compareTo(investValA) > -1 && freeAssertValB.compareTo(investValB) > -1) {
			// 持有资产价值都满足投资额度、
			log.info("freeVal >= investVal suc");
		} else {
			// 存在缺额、尝试以USDT填补额度
			BigDecimal vacancyVal = investValA.add(investValB).subtract(freeAssertValA).subtract(freeAssertValB);
			BigDecimal usdtVal = userAssetMap.getOrDefault("USDT",BigDecimal.ZERO);
			if (vacancyVal.compareTo(usdtVal) > 0) {
				log.info("usdtVal : {},vacancyVal :{}", usdtVal, vacancyVal);
				throw new RuntimeException("存在资金缺额、无法启动");
			}
			if (freeAssertValA.compareTo(investValA) < 0) {
				// 资产A存在缺额、
				vacancyVal = investValA.subtract(freeAssertValA);
				log.info("assertA vacancyVal:{}", vacancyVal);
				Order order = czClient.createBuyOfMarketOrder(symbolA, vacancyVal);
				if (!"FILLED".equals(order.getStatus())) {
					throw new RuntimeException("资产A补买失败\n" + order);
				}
				log.info("assertA buyQty :{}", order.getExecutedQty());
			}
			if (freeAssertValB.compareTo(investValB) < 0) {
				// 资产B存在缺额、
				vacancyVal = investValB.subtract(freeAssertValB);
				log.info("assertB vacancyVal:{}", vacancyVal);
				Order order = czClient.createBuyOfMarketOrder(symbolB, vacancyVal);
				if (!"FILLED".equals(order.getStatus())) {
					throw new RuntimeException("资产B补买失败\n" + order);
				}
				log.info("assertB buyQty :{}", order.getExecutedQty());
			}
		}


		/**
		 * 计算汇率和下一买入｜卖出汇率
		 */
		BigDecimal tradeRate = priceA.divide(priceB, 8, 1);

		BigDecimal gridRate = new BigDecimal(properties.getProperty("gridRate"));
		BigDecimal swapValBRate = new BigDecimal(properties.getProperty("swapValBRate"));

		Task task =
				Task.builder()
					.symbolA(symbolA).symbolB(symbolB)
					.gridRate(gridRate)
					.swapValRate(swapValBRate)
					.tradeRate(tradeRate)
					.nextBugP(tradeRate.multiply((BigDecimal.valueOf(1).subtract(gridRate))))
					.nextSellP(tradeRate.multiply((BigDecimal.valueOf(1).add(gridRate))))
					.investQtyA(investValA.divide(priceA, 8, 1))
					.investQtyB(investValB.divide(priceB, 8, 1))
					.build();
		log.info("\n" + task);
		return task;
	}
}
