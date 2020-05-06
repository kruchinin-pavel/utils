package org.kpa.utils.gui;

import javax.swing.*;
import javax.swing.text.DefaultEditorKit;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import static org.kpa.utils.gui.Parallel.swingLater;

/**
 * Mouse event generator (including context menu)
 * User: kruchinin
 * Date: 14.10.2008
 * Time: 11:52:14
 * To change this template use File | Settings | File Templates.
 */
public class DefaultContextMenu extends JPopupMenu implements MouseListener {
    private Component ctxCmp;
    private int ctxX, ctxY = 0;
    private boolean needOpenContext = false;
    private Consumer<MouseEvent> onDoubleLeftClick = e -> {
    };
    private Consumer<MouseEvent> onDoubleRightClick = e -> {
    };
    private final Timer timer = new Timer(750, e -> {
        if (needOpenContext) {
            needOpenContext = false;
            show(ctxCmp, ctxX, ctxY);
        }
    });

    public DefaultContextMenu() {
        JMenuItem copy = new JMenuItem(new DefaultEditorKit.CopyAction());
        copy.setText("Копировать {Ctrl-C}");
        copy.setMnemonic(KeyEvent.VK_C);
        add(copy);

        JMenuItem cut = new JMenuItem(new DefaultEditorKit.CutAction());
        cut.setText("Вырезать {Ctrl-X}");
        cut.setMnemonic(KeyEvent.VK_X);
        add(cut);

        JMenuItem paste = new JMenuItem(new DefaultEditorKit.PasteAction());
        paste.setText("Вставить {Ctrl-V}");
        paste.setMnemonic(KeyEvent.VK_V);
        add(paste);
    }

    protected void implMouseClicked(MouseEvent e) {
        if (e.getButton() == MouseEvent.BUTTON1) {
            if (e.getClickCount() == 2) {
                onDoubleLeftClick.accept(e);
            }
        } else if (e.getButton() == MouseEvent.BUTTON3) {
            if (e.getClickCount() == 2) {
                onDoubleRightClick.accept(e);
            }

        }
    }


    private boolean doubleClicked = false;


    protected void setNeedOpenContext(boolean needOpenContext) {
        this.needOpenContext = needOpenContext;
    }

    @Override
    final public void mouseClicked(final MouseEvent e) {
        if (e.getClickCount() == 1) {
            doubleClicked = false;
            new Thread(() -> {
                try {
                    Thread.sleep(200);
                    if (!doubleClicked) {
                        swingLater(() -> implMouseClicked(e));
                    }
                } catch (InterruptedException e1) {
                    Thread.currentThread().interrupt();
                }
            }).start();
        } else if (e.getClickCount() == 2) {
            doubleClicked = true;
            setNeedOpenContext(false);
            implMouseClicked(e);
        }
    }


    @Override
    public void mouseReleased(MouseEvent e) {
        checkTrigger(e);
    }

    private void checkTrigger(MouseEvent e) {
        if (e.isPopupTrigger() && !needOpenContext && !e.isControlDown() && !e.isAltDown()) {
            setNeedOpenContext(true);
            ctxX = e.getX();
            ctxY = e.getY();
            ctxCmp = e.getComponent();
            timer.setRepeats(false);
            timer.setDelay(100);
            timer.start();
        }
    }

    @Override
    public void mousePressed(MouseEvent e) {
        checkTrigger(e);
    }

    @Override
    public void mouseEntered(MouseEvent e) {

    }

    @Override
    public void mouseExited(MouseEvent e) {

    }

    public void setOnDoubleLeftClick(Consumer<MouseEvent> onDoubleLeftClick) {
        this.onDoubleLeftClick = onDoubleLeftClick;
    }

    public void setOnDoubleRightClick(Consumer<MouseEvent> onDoubleRightClick) {
        this.onDoubleRightClick = onDoubleRightClick;
    }

    public void register(JComponent comp) {
        if (comp instanceof JTable) {
            comp.addMouseListener(this);
            if (comp.getParent()!=null && comp.getParent().getParent() instanceof JScrollPane) {
                comp.getParent().getParent().addMouseListener(this);
            }
        } else {
            comp.setComponentPopupMenu(new DefaultContextMenu());
        }
    }

    public static DefaultContextMenu clicker(JTable table, BiConsumer<JTable, MouseEvent> onDoubleClick) {
        DefaultContextMenu popup = new DefaultContextMenu();
        popup.register(table);
        if (onDoubleClick != null) {
            popup.setOnDoubleLeftClick(e -> onDoubleClick.accept(table, e));
        }
        return popup;
    }
}
