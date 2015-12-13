package uk.co.samatkins.ecosystem.desktop;

import com.badlogic.gdx.backends.lwjgl.LwjglApplication;
import com.badlogic.gdx.backends.lwjgl.LwjglApplicationConfiguration;
import uk.co.samatkins.ecosystem.EcosystemGame;

public class DesktopLauncher {
	public static void main (String[] arg) {
		LwjglApplicationConfiguration config = new LwjglApplicationConfiguration();
		config.width = 800;
		config.height = 600;
		config.title = "Ecosystem, a game by @AtkinsSJ for LD34. http://samatkins.co.uk/";
		new LwjglApplication(new EcosystemGame(), config);
	}
}
