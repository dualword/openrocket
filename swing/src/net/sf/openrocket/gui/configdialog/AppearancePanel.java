package net.sf.openrocket.gui.configdialog;

import java.awt.Color;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.lang.reflect.Method;
import java.util.EventObject;

import javax.swing.*;
import javax.swing.colorchooser.ColorSelectionModel;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import net.miginfocom.swing.MigLayout;
import net.sf.openrocket.appearance.Appearance;
import net.sf.openrocket.appearance.AppearanceBuilder;
import net.sf.openrocket.appearance.Decal.EdgeMode;
import net.sf.openrocket.appearance.DecalImage;
import net.sf.openrocket.appearance.defaults.DefaultAppearance;
import net.sf.openrocket.document.OpenRocketDocument;
import net.sf.openrocket.gui.SpinnerEditor;
import net.sf.openrocket.gui.adaptors.BooleanModel;
import net.sf.openrocket.gui.adaptors.DecalModel;
import net.sf.openrocket.gui.adaptors.DoubleModel;
import net.sf.openrocket.gui.adaptors.EnumModel;
import net.sf.openrocket.gui.components.BasicSlider;
import net.sf.openrocket.gui.components.ColorIcon;
import net.sf.openrocket.gui.components.StyledLabel;
import net.sf.openrocket.gui.components.StyledLabel.Style;
import net.sf.openrocket.gui.components.UnitSelector;
import net.sf.openrocket.gui.util.ColorConversion;
import net.sf.openrocket.gui.util.EditDecalHelper;
import net.sf.openrocket.gui.util.EditDecalHelper.EditDecalHelperException;
import net.sf.openrocket.gui.util.SwingPreferences;
import net.sf.openrocket.l10n.Translator;
import net.sf.openrocket.rocketcomponent.ComponentChangeEvent;
import net.sf.openrocket.rocketcomponent.FinSet;
import net.sf.openrocket.rocketcomponent.InsideColorComponent;
import net.sf.openrocket.rocketcomponent.InsideColorComponentHandler;
import net.sf.openrocket.rocketcomponent.RocketComponent;
import net.sf.openrocket.startup.Application;
import net.sf.openrocket.unit.GeneralUnit;
import net.sf.openrocket.unit.Unit;
import net.sf.openrocket.unit.UnitGroup;
import net.sf.openrocket.util.LineStyle;
import net.sf.openrocket.util.StateChangeListener;

public class AppearancePanel extends JPanel {
	private static final long serialVersionUID = 2709187552673202019L;

	private static final Translator trans = Application.getTranslator();

	final private EditDecalHelper editDecalHelper = Application.getInjector()
			.getInstance(EditDecalHelper.class);

	// Outside and inside appearance builder
	final private AppearanceBuilder ab;
	private AppearanceBuilder insideAb;

	// We hang on to the user selected appearance when switching to default
	// appearance.
	// this appearance is restored if the user unchecks the "default" button.
	private Appearance previousUserSelectedAppearance = null;
	private Appearance previousUserSelectedInsideAppearance = null;

	// We cache the default appearance for this component to make switching
	// faster.
	private Appearance defaultAppearance = null;

	/**
	 * A non-unit that adjusts by a small amount, suitable for values that are
	 * on the 0-1 scale
	 */
	private final static UnitGroup TEXTURE_UNIT = new UnitGroup();
	static {
		Unit no_unit = new GeneralUnit(1, "", 2) {
			@Override
			public double getNextValue(double value) {
				return value + .1;
			}

			@Override
			public double getPreviousValue(double value) {
				return value - .1;
			}

		};
		TEXTURE_UNIT.addUnit(no_unit);
	}

	private static final JColorChooser colorChooser = new JColorChooser();

	private class ColorActionListener implements ActionListener {
		private final String valueName;
		private final Object o;

		ColorActionListener(final Object o, final String valueName) {
			this.valueName = valueName;
			this.o = o;
		}

		/**
		 Changes the color of the selected component to <color>
		 @param color: color to change the component to
		 */
		private void changeComponentColor(Color color) {
			try {
				final Method setMethod = o.getClass().getMethod(
						"set" + valueName, net.sf.openrocket.util.Color.class);
				if (color == null)
					return;
				try {
					setMethod.invoke(o, ColorConversion
							.fromAwtColor(color));
				} catch (Throwable e1) {
					Application.getExceptionHandler()
							.handleErrorCondition(e1);
				}
			} catch (Throwable e1) {
				Application.getExceptionHandler().handleErrorCondition(e1);
			}

		}

		/**
		 * Change the component's preview color upon a change in color pick. If the user clicks 'OK' in the
		 * dialog window, the selected color is assigned to the component. If 'Cancel' was clicked, the component's
		 * color will be reverted to its initial color (color before the appearance editor opened).
		 */
		@Override
		public void actionPerformed(ActionEvent colorClickEvent) {
			try {
				final Method getMethod = o.getClass().getMethod(
						"get" + valueName);
				net.sf.openrocket.util.Color c = (net.sf.openrocket.util.Color) getMethod
						.invoke(o);

				Color awtColor = ColorConversion.toAwtColor(c);
				colorChooser.setColor(awtColor);

				// Bind a change of color selection to a change in the components color
				ColorSelectionModel model = colorChooser.getSelectionModel();
				ChangeListener changeListener = new ChangeListener() {
					public void stateChanged(ChangeEvent changeEvent) {
						Color selected = colorChooser.getColor();
						changeComponentColor(selected);
					}
				};
				model.addChangeListener(changeListener);

				JDialog d = JColorChooser.createDialog(AppearancePanel.this,
						trans.get("RocketCompCfg.lbl.Choosecolor"), true,
						colorChooser, new ActionListener() {
							@Override
							public void actionPerformed(ActionEvent okEvent) {
								changeComponentColor(colorChooser.getColor());
								// Unbind listener to avoid the current component's appearance to change with other components
								model.removeChangeListener(changeListener);
							}
						}, new ActionListener() {
							@Override
							public void actionPerformed(ActionEvent cancelEvent) {
								changeComponentColor(awtColor);
								// Unbind listener to avoid the current component's appearance to change with other components
								model.removeChangeListener(changeListener);
							}
						});
				d.setVisible(true);
			} catch (Throwable e1) {
				Application.getExceptionHandler().handleErrorCondition(e1);
			}
		}
	}


	public AppearancePanel(final OpenRocketDocument document,
			final RocketComponent c) {
		super(new MigLayout("fill", "[150][grow][150][grow]"));

		defaultAppearance = DefaultAppearance.getDefaultAppearance(c);

		previousUserSelectedAppearance = c.getAppearance();
		if (previousUserSelectedAppearance == null) {
			previousUserSelectedAppearance = new AppearanceBuilder()
					.getAppearance();
			ab = new AppearanceBuilder(defaultAppearance);
		} else {
			ab = new AppearanceBuilder(previousUserSelectedAppearance);
		}

		if (c instanceof InsideColorComponent) {
			previousUserSelectedInsideAppearance = ((InsideColorComponent) c).getInsideColorComponentHandler()
					.getInsideAppearance();
			if (previousUserSelectedInsideAppearance == null) {
				previousUserSelectedInsideAppearance = new AppearanceBuilder()
						.getAppearance();
				insideAb = new AppearanceBuilder(defaultAppearance);
			} else {
				insideAb = new AppearanceBuilder(previousUserSelectedInsideAppearance);
			}
		}

		net.sf.openrocket.util.Color figureColor = c.getColor();
		if (figureColor == null) {
			figureColor = Application.getPreferences().getDefaultColor(
					c.getClass());
		}
		final JButton figureColorButton = new JButton(
				new ColorIcon(figureColor));

		ab.addChangeListener(new StateChangeListener() {
			@Override
			public void stateChanged(EventObject e) { figureColorButton.setIcon(new ColorIcon(c.getColor())); }
		});

		c.addChangeListener(new StateChangeListener() {
			@Override
			public void stateChanged(EventObject e) {
				net.sf.openrocket.util.Color col = c.getColor();
				if (col == null) {
					col = Application.getPreferences().getDefaultColor(
							c.getClass());
				}
				figureColorButton.setIcon(new ColorIcon(col));
			}
		});

		figureColorButton
				.addActionListener(new ColorActionListener(c, "Color"));

		BooleanModel fDefault = new BooleanModel(c.getColor() == null);

		{// Style Header Row
			final JCheckBox colorDefault = new JCheckBox(fDefault);
			colorDefault.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent e) {
					if (colorDefault.isSelected()) {
						c.setColor(null);
						c.setLineStyle(null);
					} else {
						c.setColor(((SwingPreferences) Application
								.getPreferences()).getDefaultColor(c.getClass()));
						c.setLineStyle(((SwingPreferences) Application
								.getPreferences()).getDefaultLineStyle(c
								.getClass()));
					}
				}
			});
			colorDefault.setText(trans
					.get("RocketCompCfg.checkbox.Usedefaultcolor"));
			add(new StyledLabel(trans.get("RocketCompCfg.lbl.Figurestyle"),
					Style.BOLD));
			add(colorDefault);

			JButton button = new JButton(
					trans.get("RocketCompCfg.but.Saveasdefstyle"));
			button.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent e) {
					if (c.getColor() != null) {
						((SwingPreferences) Application.getPreferences())
								.setDefaultColor(c.getClass(), c.getColor());
						c.setColor(null);
					}
					if (c.getLineStyle() != null) {
						Application.getPreferences().setDefaultLineStyle(
								c.getClass(), c.getLineStyle());
						c.setLineStyle(null);
					}
				}
			});
			fDefault.addEnableComponent(button, false);
			add(button, "span 2, align right, wrap");
		}

		{// Figure Color
			add(new JLabel(trans.get("RocketCompCfg.lbl.Componentcolor")));
			fDefault.addEnableComponent(figureColorButton, false);
			add(figureColorButton);
		}

		{// Line Style
			add(new JLabel(trans.get("RocketCompCfg.lbl.Complinestyle")));

			LineStyle[] list = new LineStyle[LineStyle.values().length + 1];
			System.arraycopy(LineStyle.values(), 0, list, 1,
					LineStyle.values().length);

			final JComboBox<LineStyle> combo = new JComboBox<LineStyle>(new EnumModel<LineStyle>(c,
					"LineStyle",
					// // Default style
					list, trans.get("LineStyle.Defaultstyle")));

			fDefault.addEnableComponent(combo, false);

			add(combo, "wrap");
		}

		add(new JSeparator(SwingConstants.HORIZONTAL), "span, wrap, growx");

		// Display a tabbed panel for choosing the outside and inside appearance, if the object is of type InsideColorComponent
		if (c instanceof InsideColorComponent) {
			InsideColorComponentHandler handler = ((InsideColorComponent)c).getInsideColorComponentHandler();

			JTabbedPane tabbedPane = new JTabbedPane();
			JPanel outsidePanel = new JPanel(new MigLayout("fill", "[150][grow][150][grow]"));
			JPanel insidePanel = new JPanel(new MigLayout("fill", "[150][grow][150][grow]"));

			appearanceSection(document, c, false, outsidePanel);
			appearanceSection(document, c, true, insidePanel);

			// Get translator keys
			String tr_outside, tr_inside, tr_edges, tr_edges_ttip;
			if (c instanceof FinSet) {
				tr_outside = "RocketCompCfg.tab.RightSide";
				tr_inside = "RocketCompCfg.tab.LeftSide";
				tr_edges = "AppearanceCfg.lbl.EdgesSameAsLeftSide";
				tr_edges_ttip = "AppearanceCfg.lbl.ttip.EdgesSameAsLeftSide";
			}
			else {
				tr_outside = "RocketCompCfg.tab.Outside";
				tr_inside = "RocketCompCfg.tab.Inside";
				tr_edges = "AppearanceCfg.lbl.EdgesSameAsInside";
				tr_edges_ttip = "AppearanceCfg.lbl.ttip.EdgesSameAsInside";
			}

			tabbedPane.addTab(trans.get(tr_outside), null, outsidePanel,
					"Outside Tool Tip");
			tabbedPane.addTab(trans.get(tr_inside), null, insidePanel,
					"Inside Tool Tip");
			add(tabbedPane, "span 4, growx, wrap");

			// Checkbox to set edges the same as inside/outside
			BooleanModel b = new BooleanModel(handler.isEdgesSameAsInside());
			JCheckBox edges = new JCheckBox(b);
			edges.setText(trans.get(tr_edges));
			edges.setToolTipText(trans.get(tr_edges_ttip));
			add(edges, "wrap");

			edges.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent e) {
					handler.setEdgesSameAsInside(edges.isSelected());
					c.fireComponentChangeEvent(ComponentChangeEvent.NONFUNCTIONAL_CHANGE);
				}
			});
		}
		else
			appearanceSection(document, c, false, this);
	}

	/**
	 *
	 * @param document
	 * @param c
	 * @param insideBuilder flag to check whether you are on the inside builder (true) or outside builder
	 * @param panel
	 */
	private void appearanceSection(OpenRocketDocument document, RocketComponent c,
								   boolean insideBuilder, JPanel panel) {
		AppearanceBuilder builder;
		BooleanModel mDefault;
		if (!insideBuilder) {
			builder = ab;
			mDefault = new BooleanModel(c.getAppearance() == null);
		}
		else if (c instanceof InsideColorComponent) {
			builder = insideAb;
			mDefault = new BooleanModel(
					((InsideColorComponent)c).getInsideColorComponentHandler().getInsideAppearance() == null);
		}
		else return;

		DecalModel decalModel = new DecalModel(panel, document, builder);
		JComboBox<DecalImage> textureDropDown = new JComboBox<DecalImage>(decalModel);

		JButton colorButton = new JButton(new ColorIcon(builder.getPaint()));

		builder.addChangeListener(new StateChangeListener() {
			@Override
			public void stateChanged(EventObject e) {
				colorButton.setIcon(new ColorIcon(builder.getPaint()));
				if (!insideBuilder)
					c.setAppearance(builder.getAppearance());
				else
					((InsideColorComponent)c).getInsideColorComponentHandler().setInsideAppearance(builder.getAppearance());
				decalModel.refresh();
			}
		});

		colorButton.addActionListener(new ColorActionListener(builder, "Paint"));

		// Texture Header Row
		panel.add(new StyledLabel(trans.get("AppearanceCfg.lbl.Appearance"),
				Style.BOLD));
		JCheckBox materialDefault = new JCheckBox(mDefault);
		materialDefault.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				if (materialDefault.isSelected()) {
					if (!insideBuilder) {
						previousUserSelectedAppearance = (builder == null) ? null
								: builder.getAppearance();
					}
					else {
						previousUserSelectedInsideAppearance = (builder == null) ? null
								: builder.getAppearance();
					}
					builder.setAppearance(defaultAppearance);
				} else {
					if (!insideBuilder)
						builder.setAppearance(previousUserSelectedAppearance);
					else
						builder.setAppearance(previousUserSelectedInsideAppearance);
				}
			}
		});
		materialDefault.setText(trans.get("AppearanceCfg.lbl.Usedefault"));
		if (insideBuilder)
			panel.add(materialDefault);
		else
			panel.add(materialDefault, "wrap");

		// Get translation keys
		String tr_insideOutside, tr_insideOutside_ttip;
		if (c instanceof FinSet) {
			tr_insideOutside = "AppearanceCfg.lbl.LeftSideSameAsRightSide";
			tr_insideOutside_ttip = "AppearanceCfg.lbl.ttip.LeftSideSameAsRightSide";
		}
		else {
			tr_insideOutside = "AppearanceCfg.lbl.InsideSameAsOutside";
			tr_insideOutside_ttip = "AppearanceCfg.lbl.ttip.InsideSameAsOutside";
		}

		// Custom inside color
		if (insideBuilder) {
			InsideColorComponentHandler handler = ((InsideColorComponent)c).getInsideColorComponentHandler();
			BooleanModel b = new BooleanModel(handler.isInsideSameAsOutside());
			JCheckBox customInside = new JCheckBox(b);
			customInside.setText(trans.get(tr_insideOutside));
			customInside.setToolTipText(trans.get(tr_insideOutside_ttip));
			customInside.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent e) {
					handler.setInsideSameAsOutside(customInside.isSelected());
					c.fireComponentChangeEvent(ComponentChangeEvent.NONFUNCTIONAL_CHANGE);
				}
			});
			panel.add(customInside, "wrap");
		}

		// Texture File
		panel.add(new JLabel(trans.get("AppearanceCfg.lbl.Texture")));
		JPanel p = new JPanel(new MigLayout("fill, ins 0", "[grow][]"));
		mDefault.addEnableComponent(textureDropDown, false);
		p.add(textureDropDown, "grow");
		panel.add(p, "span 3, growx, wrap");
		JButton editBtn = new JButton(
				trans.get("AppearanceCfg.but.edit"));
		editBtn.setEnabled(builder.getImage() != null);
		// Enable the editBtn only when the appearance builder has an Image
		// assigned to it.
		builder.addChangeListener(new StateChangeListener() {
			@Override
			public void stateChanged(EventObject e) {
				editBtn.setEnabled(builder.getImage() != null);
			}
		});

		editBtn.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				try {
					DecalImage newImage = editDecalHelper.editDecal(
							SwingUtilities
									.getWindowAncestor(panel),
							document, c, builder.getImage(), insideBuilder);
					builder.setImage(newImage);
				} catch (EditDecalHelperException ex) {
					JOptionPane.showMessageDialog(panel,
							ex.getMessage(), "", JOptionPane.ERROR_MESSAGE);
				}
			}

		});
		p.add(editBtn);

		// Color
		panel.add(new JLabel(trans.get("AppearanceCfg.lbl.color.Color")));
		mDefault.addEnableComponent(colorButton, false);
		panel.add(colorButton);

		// Scale
		panel.add(new JLabel(trans.get("AppearanceCfg.lbl.texture.scale")));

		panel.add(new JLabel("x:"), "split 4");
		JSpinner scaleU = new JSpinner(new DoubleModel(builder, "ScaleX",
				TEXTURE_UNIT).getSpinnerModel());
		scaleU.setEditor(new SpinnerEditor(scaleU));
		mDefault.addEnableComponent(scaleU, false);
		panel.add(scaleU, "w 40");

		panel.add(new JLabel("y:"));
		JSpinner scaleV = new JSpinner(new DoubleModel(builder, "ScaleY",
				TEXTURE_UNIT).getSpinnerModel());
		scaleV.setEditor(new SpinnerEditor(scaleV));
		mDefault.addEnableComponent(scaleV, false);
		panel.add(scaleV, "wrap, w 40");

		// Shine
		panel.add(new JLabel(trans.get("AppearanceCfg.lbl.shine")));
		DoubleModel shineModel = new DoubleModel(builder, "Shine",
				UnitGroup.UNITS_RELATIVE);
		JSpinner spin = new JSpinner(shineModel.getSpinnerModel());
		spin.setEditor(new SpinnerEditor(spin));
		JSlider slide = new JSlider(shineModel.getSliderModel(0, 1));
		UnitSelector unit = new UnitSelector(shineModel);

		mDefault.addEnableComponent(slide, false);
		mDefault.addEnableComponent(spin, false);
		mDefault.addEnableComponent(unit, false);

		panel.add(spin, "split 3, w 50");
		panel.add(unit);
		panel.add(slide, "w 50");

		// Offset
		panel.add(new JLabel(trans.get("AppearanceCfg.lbl.texture.offset")));

		panel.add(new JLabel("x:"), "split 4");
		JSpinner offsetU = new JSpinner(new DoubleModel(builder, "OffsetU",
				TEXTURE_UNIT).getSpinnerModel());
		offsetU.setEditor(new SpinnerEditor(offsetU));
		mDefault.addEnableComponent(offsetU, false);
		panel.add(offsetU, "w 40");

		panel.add(new JLabel("y:"));
		JSpinner offsetV = new JSpinner(new DoubleModel(builder, "OffsetV",
				TEXTURE_UNIT).getSpinnerModel());
		offsetV.setEditor(new SpinnerEditor(offsetV));
		mDefault.addEnableComponent(offsetV, false);
		panel.add(offsetV, "wrap, w 40");

		// Repeat
		panel.add(new JLabel(trans.get("AppearanceCfg.lbl.texture.repeat")));
		EdgeMode[] list = new EdgeMode[EdgeMode.values().length];
		System.arraycopy(EdgeMode.values(), 0, list, 0,
				EdgeMode.values().length);
		JComboBox<EdgeMode> combo = new JComboBox<EdgeMode>(new EnumModel<EdgeMode>(builder,
				"EdgeMode", list));
		mDefault.addEnableComponent(combo, false);
		panel.add(combo);

		// Rotation
		panel.add(new JLabel(trans.get("AppearanceCfg.lbl.texture.rotation")));
		DoubleModel rotationModel = new DoubleModel(builder, "Rotation",
				UnitGroup.UNITS_ANGLE);
		JSpinner rotation = new JSpinner(rotationModel.getSpinnerModel());
		rotation.setEditor(new SpinnerEditor(rotation));
		mDefault.addEnableComponent(rotation, false);
		panel.add(rotation, "split 3, w 50");
		panel.add(new UnitSelector(rotationModel));
		BasicSlider bs = new BasicSlider(rotationModel.getSliderModel(
				-Math.PI, Math.PI));
		mDefault.addEnableComponent(bs, false);
		panel.add(bs, "w 50, wrap");
	}
}
