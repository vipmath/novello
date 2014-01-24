package com.welty.othello.gui;

import com.orbanova.common.jsb.Grid;
import com.welty.novello.core.State;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;

/**
 */
public class PlayerPanel extends Grid<JComponent>  implements GameModel.ChangeListener{
    private final JLabel name;
    private final JLabel time = new JLabel();
    private final boolean isBlack;
    private final @NotNull GameModel gameModel;

    private static final int borderSize = 4;

    private static final Border moverBorder = BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(Color.RED), BorderFactory.createEmptyBorder(borderSize-1,borderSize-1,borderSize-1,borderSize-1));
    private static final Border enemyBorder = BorderFactory.createEmptyBorder(borderSize,borderSize,borderSize,borderSize);

    PlayerPanel(boolean isBlack, @NotNull GameModel gameModel) {
        super(2);
        this.isBlack = isBlack;
        this.gameModel = gameModel;
        final ImageIcon icon = new ImageIcon(isBlack ? Images.smallBlack : Images.smallWhite);
        name = new JLabel(icon);
        add(time);
        add(name);
        spacing(5);
        gameModel.addChangeListener(this);
        gameViewChanged();
    }

    @Override public void gameViewChanged() {
        name.setText(isBlack? gameModel.getBlackName() : gameModel.getWhiteName());
        final State state = gameModel.getState();
        time.setText((isBlack? state.blackClock : state.whiteClock).toString());
        final boolean useBorder = state.position.blackToMove == isBlack;
        final Border border = useBorder?moverBorder : enemyBorder;
        setBorder(border);
    }
}
