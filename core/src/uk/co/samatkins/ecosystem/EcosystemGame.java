package uk.co.samatkins.ecosystem;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.viewport.ScreenViewport;

import java.util.Random;

public class EcosystemGame extends ApplicationAdapter {

	enum Terrain {
		Air(null),
		Soil(new Texture("soil.png"));

		public final Texture texture;

		Terrain(Texture texture) {
			this.texture = texture;
		}
	}

	class Tile {
		Terrain terrain = Terrain.Air;
		float humidity = 0;
	}

	enum InteractionMode {
		Water;
	}

	class Droplet {
		float x, y;
		float dx, dy;

		public Droplet(float x, float y, float dx, float dy) {
			this.x = x;
			this.y = y;
			this.dx = dx;
			this.dy = dy;
		}
	}

	SpriteBatch batch;
	OrthographicCamera camera;
	private ScreenViewport viewport;
	private final Vector3 mousePos = new Vector3();

	int worldWidth, worldHeight;
	Tile[][] tiles;
	InteractionMode interactionMode = InteractionMode.Water;


	Texture texDroplet;
	final int maxDroplets = 128;
	final Array<Droplet> droplets = new Array<Droplet>(false, maxDroplets);

	final Color colNoHumidity = new Color(1,1,1,1),
				colMaxHumidity = new Color(0,0,1,1);
	final Color dumpColor = new Color();

	public static int randomInt(Random random, int minInclusive, int maxExclusive) {
		return minInclusive + random.nextInt(maxExclusive - minInclusive);
	}
	
	@Override
	public void create () {
		batch = new SpriteBatch();
		camera = new OrthographicCamera(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
		viewport = new ScreenViewport(camera);
		viewport.setUnitsPerPixel(1f/16f);

		texDroplet = new Texture("raindrop.png");
		droplets.clear();

		// Really crummy terrain generation
		worldWidth = 40;
		worldHeight = 30;
		tiles = new Tile[worldWidth][worldHeight];
		Random random = new Random();

		int depth = randomInt(random, 1, 10);

		for (int x = 0; x < worldWidth; x++) {

			depth = randomInt(random, Math.max(1, depth - 2), Math.min(10, depth + 3));

			for (int y = 0; y < worldHeight; y++) {
				Tile tile = new Tile();
				if (y < depth) {
					tile.terrain = Terrain.Soil;
					tile.humidity = random.nextFloat();
				} else {
					tile.terrain = Terrain.Air;
				}
				tiles[x][y] = tile;
			}
		}
	}

	@Override
	public void render () {

		float dt = Gdx.graphics.getDeltaTime();

		// Camera controls
		final float scrollSpeed = 15f * dt;
		if (Gdx.input.isKeyPressed(Input.Keys.LEFT) || Gdx.input.isKeyPressed(Input.Keys.A)) {
			camera.translate(-scrollSpeed, 0f);
		} else if (Gdx.input.isKeyPressed(Input.Keys.RIGHT) || Gdx.input.isKeyPressed(Input.Keys.D)) {
			camera.translate(scrollSpeed, 0f);
		}
		if (Gdx.input.isKeyPressed(Input.Keys.UP) || Gdx.input.isKeyPressed(Input.Keys.W)) {
			camera.translate(0f, scrollSpeed);
		} else if (Gdx.input.isKeyPressed(Input.Keys.DOWN) || Gdx.input.isKeyPressed(Input.Keys.S)) {
			camera.translate(0f, -scrollSpeed);
		}
		camera.update();

		mousePos.set(Gdx.input.getX(), Gdx.input.getY(), 0f);
		camera.unproject(mousePos);

		// Click for rain!
		if (interactionMode == InteractionMode.Water) {
			if (Gdx.input.isButtonPressed(Input.Buttons.LEFT)) {
				if (droplets.size < maxDroplets) {
					droplets.add(new Droplet(mousePos.x, mousePos.y, 0f, -10f));
				}
			}
		}

		// Update droplets
		for (int i = 0; i < droplets.size; i++) {

			Droplet droplet = droplets.get(i);
			droplet.x += dt * droplet.dx;
			droplet.y += dt * droplet.dy;

			int tx = (int) droplet.x,
				ty = (int) droplet.y;
			if ((tx < 0) || (tx >= worldWidth)
				|| (ty < 0) || (ty >= worldHeight)) {
				droplets.removeIndex(i);
			} else {
				// Water the ground!
				Tile tile = tiles[tx][ty];
				if (tile.terrain != Terrain.Air) {
					// Raindrops keep falling on my head
					tile.humidity += 0.1f;
					droplets.removeIndex(i);
				}
			}
		}

		// Update humidity
		for (int x = 0; x < worldWidth; x++) {
			for (int y = 0; y < worldHeight; y++) {
				Tile tile = tiles[x][y];

				if (tile.terrain != Terrain.Air) { // TODO: Air humidity???

					Tile above = (y < (worldHeight-1)) ? tiles[x][y + 1] : null;
					Tile below = (y > 0) ? tiles[x][y - 1] : null;
					Tile left = (x > 0) ? tiles[x-1][y] : null;
					Tile right = (x < (worldWidth - 1)) ? tiles[x+1][y] : null;

					// Evaporation
					if (above != null) {
						if (above.terrain == Terrain.Air) {
							tile.humidity *= 0.999f;
						}
					}

					// Osmosis
					transferHumidity(tile, above);
					transferHumidity(tile, below);
					transferHumidity(tile, left);
					transferHumidity(tile, right);
				}
			}
		}

		Gdx.gl.glClearColor((113f/255f), (149f/255f), (255f/255f), 1);
		Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
		batch.setProjectionMatrix(camera.combined);
		batch.begin();

		// Draw terrain
		for (int x = 0; x < worldWidth; x++) {
			for (int y = 0; y < worldHeight; y++) {
				Tile tile = tiles[x][y];
				Texture texture = tile.terrain.texture;
				if (texture != null) {

					// LibGDX is STUPID why does lerping a color edit the color??!?!??!?!?!?
					dumpColor.set(colNoHumidity);
					batch.setColor(dumpColor.lerp(colMaxHumidity, tile.humidity));
					batch.draw(texture, x, y, 1, 1);
				}
			}
		}

		// Draw droplets
		batch.setColor(Color.WHITE);
		for (Droplet droplet : droplets) {
			batch.draw(texDroplet, droplet.x, droplet.y, 0.5f, 0.5f);
		}

		batch.end();
	}

	@Override
	public void resize(int width, int height) {
		super.resize(width, height);
		viewport.update(width, height);
	}

	private void transferHumidity(Tile source, Tile dest) {
		if ((dest != null)
			&& (dest.terrain != Terrain.Air)) {

			float difference = source.humidity - dest.humidity;
			if (difference > 0) {
				float exchange = difference * 0.002f; // Tweak this to adjust speed of equalisation, bigger = faster
				source.humidity -= exchange;
				dest.humidity += exchange;
			}
		}
	}
}
