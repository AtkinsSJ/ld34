package uk.co.samatkins.ecosystem.client;

import com.badlogic.gdx.ApplicationListener;
import com.badlogic.gdx.backends.gwt.GwtApplication;
import com.badlogic.gdx.backends.gwt.GwtApplicationConfiguration;
import uk.co.samatkins.ecosystem.EcosystemGame;

public class HtmlLauncher extends GwtApplication {

        @Override
        public GwtApplicationConfiguration getConfig () {
                return new GwtApplicationConfiguration(960, 600);
        }

        @Override
        public ApplicationListener getApplicationListener () {
                return new EcosystemGame();
        }
}