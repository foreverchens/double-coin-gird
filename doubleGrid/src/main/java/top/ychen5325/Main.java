package top.ychen5325;

import lombok.extern.slf4j.Slf4j;

/**
 * @author yyy
 * @wx ychen5325
 * @email q1416349095@gmail.com
 */
@Slf4j
public class Main {
	public static void main(String[] args) {
		Grid grid = new Grid();
		grid.loop(grid.init());
	}
}