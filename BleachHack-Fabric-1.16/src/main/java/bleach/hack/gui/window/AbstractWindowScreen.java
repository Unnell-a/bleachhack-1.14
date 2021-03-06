/*
 * This file is part of the BleachHack distribution (https://github.com/BleachDrinker420/bleachhack-1.14/).
 * Copyright (c) 2019 Bleach.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package bleach.hack.gui.window;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map.Entry;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.stream.Collectors;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;

public abstract class AbstractWindowScreen extends Screen {

	private List<Window> windows = new ArrayList<>();
	
	/* [Layer, Window Index] */
	private SortedMap<Integer, Integer> windowOrder = new TreeMap<>(); 

	public AbstractWindowScreen(Text text_1) {
		super(text_1);
	}
	
	public void addWindow(Window window) {
		windows.add(window);
		windowOrder.put(windows.size() - 1, windows.size() - 1);
	}
	
	public Window getWindow(int i) {
		return windows.get(i);
	}
	
	public void clearWindows() {
		windows.clear();
		windowOrder.clear();
	}
	
	public List<Window> getWindows() {
		return windows;
	}
	
	protected List<Integer> getWindowsBackToFront() {
		return windowOrder.values().stream().collect(Collectors.toList());
	}
		
	protected List<Integer> getWindowsFrontToBack() {
		List<Integer> w = getWindowsBackToFront();
		Collections.reverse(w);
		return w;
	}
	
	protected int getSelectedWindow() {
		for (int i = 0; i < windows.size(); i++) {
			if (!getWindow(i).closed && getWindow(i).selected) {
				return i;
			}
		}
		
		return -1;
	}

	public void render(MatrixStack matrix, int mouseX, int mouseY, float delta) {
		int sel = getSelectedWindow();
		
		if (sel == -1) {
			for (int i: getWindowsFrontToBack()) {
				if (!getWindow(i).closed) {
					selectWindow(i);
					break;
				}
			}
		}

		boolean close = true;
		
		for (int w: getWindowsBackToFront()) {
			if (!getWindow(w).closed) {
				close = false;
				onRenderWindow(matrix, w, mouseX, mouseY);
			}
		}
		
		if (close) this.onClose();

		super.render(matrix, mouseX, mouseY, delta);
	}

	public void onRenderWindow(MatrixStack matrix, int window, int mX, int mY) {
		if (!windows.get(window).closed) {
			windows.get(window).render(matrix, mX, mY);
		}
	}

	public void selectWindow(int window) {
		for (Window w: windows) {
			if (w.selected) {
				w.inactiveTime = 2;
			}
			
			w.selected = false;
		}
		
		for (int i = 0; i < windows.size(); i++) {
			Window w = windows.get(i);

			if (i == window) {
				w.selected = true;
				int index = -1;
				for (Entry<Integer, Integer> e: windowOrder.entrySet()) {
					if (e.getValue() == window) {
						index = e.getKey();
						break;
					}
				}

				windowOrder.remove(index);
				for (Entry<Integer, Integer> e: new TreeMap<>(windowOrder).entrySet()) {
					if (e.getKey() > index) {
						windowOrder.remove(e.getKey());
						windowOrder.put(e.getKey() - 1, e.getValue());
					}
				}

				windowOrder.put(windowOrder.size(), window);
			}
		}
	}

	public boolean mouseClicked(double double_1, double double_2, int int_1) {
		/* Handle what window will be selected when clicking */
		for (int wi: getWindowsFrontToBack()) {
			Window w = getWindow(wi);

			if (w.inactiveTime <= 0 && double_1 > w.x1 && double_1 < w.x2 && double_2 > w.y1 && double_2 < w.y2 && !w.closed) {
				if (w.shouldClose((int) double_1, (int) double_2)) {
					w.closed = true;
					break;
				}
				
				if (w.selected) {
					w.onMousePressed((int) double_1, (int) double_2);
				} else {
					selectWindow(wi);
				}
				
				break;
			}
		}
		return super.mouseClicked(double_1, double_2, int_1);
	}

	public boolean mouseReleased(double double_1, double double_2, int int_1) {
		for (Window w : windows) {
			w.onMouseReleased((int) double_1, (int) double_2);
		}

		return super.mouseReleased(double_1, double_2, int_1);
	}

}
