package top.ychen5325;

import lombok.extern.slf4j.Slf4j;

/**
 * @author yyy
 */
@Slf4j
public class Main {
	public static void main(String[] args) {
		Grid grid = new Grid();
		grid.loop(grid.init());
	}
}