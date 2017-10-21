/*-----------------------------------------------------------------------
 *
 * Copyright (C) 2017 Sonalysts Inc. All Rights Reserved.
 *
 * Revision History:
 * Date     Name         Description
 * ------   ---------    -------------------------------------------------
 * 02/2017  G. Lucas     Created
 *
 * Notes:
 *
 *--------------------------------------------------------------------------
 */
package tinfour.test.viewer;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import javax.swing.JButton;
import javax.swing.JColorChooser;
import javax.swing.JDialog;
import javax.swing.colorchooser.ColorSelectionModel;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;


public class ColorButton extends JButton {
    private static final long serialVersionUID = 1L;
    Color colorChoice = Color.white;
    JColorChooser chooser;
    JDialog dialog;
    private static final String defaultTitle = "Select a color";

    public ColorButton() {
        super();
        final ColorButton self = this;
        this.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (dialog != null) {
                    int baseX = getX();
                    int baseY = getY();
                    dialog.setLocation(baseX, baseY);
                    dialog.setVisible(true);
                    dialog.toFront();

                    return;
                }
                if (chooser == null) {
                    chooser = new JColorChooser(colorChoice);
                    final ColorSelectionModel model = chooser.getSelectionModel();
                    model.addChangeListener(new ChangeListener() {
                        @Override
                        public void stateChanged(ChangeEvent e) {
                            colorChoice = model.getSelectedColor();
                        }
                    });
                }
                ActionListener okListener = new ActionListener() {
                    @Override
                    public void actionPerformed(ActionEvent e) {
                        colorChoice = chooser.getColor();

                    }
                };

                String title = defaultTitle;
                String n = getToolTipText();
                if(n !=null && !n.isEmpty()){
                    title = n;
                }
                dialog = JColorChooser.createDialog(
                        self,
                        title,
                        false,
                        chooser, okListener, null);
                dialog.addWindowListener(new WindowAdapter() {
                    @Override
                    public void windowClosed(WindowEvent e) {
                        dialog = null;
                    }
                });

                dialog.setVisible(true);
                int baseX = getX();
                int baseY = getY();
                dialog.setLocation(baseX, baseY);
            }
        });

    }

    public void setColor(Color color) {
        colorChoice = color;
        if(chooser!=null){
            chooser.setColor(color);
        }
        repaint();
    }

    public Color getColor(){
        return colorChoice;
    }

    @Override
    public void paintComponent(Graphics g) {
        super.paintComponent(g);
        int w = getWidth();
        int h = getHeight();
        Insets i = getInsets();
        int fx = i.left;
        int fy = i.top;
        int fw = w - fx - i.right;
        int fh = h - fy - i.bottom;
        g.setColor(colorChoice);
        g.fillRect(fx, fy, fw, fh);
        super.paintBorder(g);
    }
}
