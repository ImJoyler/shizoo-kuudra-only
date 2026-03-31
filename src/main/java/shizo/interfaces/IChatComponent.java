package shizo.interfaces;

import net.minecraft.client.GuiMessage;
import java.util.List;

public interface IChatComponent {
    double getMouseXtoChatX();
    double getMouseYtoChatY();
    double getLineIndex(double x, double y);
    List<GuiMessage.Line> getVisibleMessages();
}