package top.ychen5325;/**
 *
 */

import lombok.Builder;
import lombok.Data;
import lombok.ToString;

import java.math.BigDecimal;

/**
 * @author yyy
 */
@Data
@Builder
@ToString
public class Task {

	/**
	 * 相对价值资产
	 */
	String symbolA;
	/**
	 * 稍逊资产
	 */
	String symbolB;

	/**
	 * A资产投资额度
	 */
	BigDecimal investQtyA;
	/**
	 * B资产投资额度
	 */
	BigDecimal investQtyB;

	/**
	 * 网格收益率
	 */
	BigDecimal gridRate;

	/**
	 * 对B资产的单次交换价值比率
	 * 如SwapValBRate=10、则单次交换价值为 SwapValBRatio/100 * assertValB
	 */
	BigDecimal swapValRate;

	BigDecimal tradeRate;

	BigDecimal nextBugP;

	BigDecimal nextSellP;
}
