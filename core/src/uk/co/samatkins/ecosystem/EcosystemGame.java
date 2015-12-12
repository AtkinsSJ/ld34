package uk.co.samatkins.ecosystem;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.NinePatch;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.viewport.ScreenViewport;

import java.util.Random;

import static uk.co.samatkins.ecosystem.EcosystemGame.InteractionMode.Water;

public class EcosystemGame extends ApplicationAdapter {

	private Random random;

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
		Plant plant = null;
	}

	enum InteractionMode {
		Water,
		PlantSeed;
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

	enum PlantType {
		Leafy(new Texture("plant1.png"), new Texture("seed1.png"));

		final Texture plantTexture, seedTexture;

		PlantType(Texture plantTexture, Texture seedTexture) {
			this.plantTexture = plantTexture;
			this.seedTexture = seedTexture;
		}
	}

	class Seed {
		PlantType type;
		float x, y;
		float dy;

		public Seed(float x, float y, PlantType type) {
			this.x = x;
			this.y = y;
			this.type = type;

			this.dy = 0;
		}
	}

	class Plant {
		PlantType type;
		int x, y;

		float water;

		public Plant(PlantType type, int x, int y) {
			this.type = type;
			this.x = x;
			this.y = y;

			this.water = 0.5f;
		}
	}

	SpriteBatch batch;
	OrthographicCamera camera, uiCamera;
	ScreenViewport viewport;
	final Vector3 mousePos = new Vector3();
	final Vector3 uiMousePos = new Vector3();
	boolean mouseWasDown = false;

	int worldWidth, worldHeight;
	Tile[][] tiles;
	InteractionMode interactionMode = Water;

	NinePatch buttonBackground, buttonOverBackground, buttonHitBackground;
	Texture texCloud;

	final Array<Seed> seeds = new Array<Seed>(false, 128);
	final Array<Plant> plants = new Array<Plant>(false, 128);

	Texture texDroplet;
	final Array<Droplet> droplets = new Array<Droplet>(false, 128);

	final Color colNoHumidity = new Color(1,1,1,1),
				colMaxHumidity = new Color(0,0,1,1);
	final Color colPlantDry = new Color(207f/255f, 74f/255f, 45f/255f,1),
				colPlantWet = new Color(1,1,1,1);
	final Color dumpColor = new Color();

	public static int randomInt(Random random, int minInclusive, int maxExclusive) {
		return minInclusive + random.nextInt(maxExclusive - minInclusive);
	}
	
	@Override
	public void create () {
		batch = new SpriteBatch();
		camera = new OrthographicCamera();
		viewport = new ScreenViewport(camera);
		uiCamera = new OrthographicCamera();

		texDroplet = new Texture("raindrop.png");
		texCloud = new Texture("cloud.png");
		buttonBackground = new NinePatch(new Texture("button.png"), 6, 6, 6, 6);
		buttonOverBackground = new NinePatch(new Texture("button-over.png"), 6, 6, 6, 6);
		buttonHitBackground = new NinePatch(new Texture("button-hit.png"), 6, 6, 6, 6);
		droplets.clear();
		seeds.clear();
		plants.clear();

		// Really crummy terrain generation
		worldWidth = 80;
		worldHeight = 40;
		tiles = new Tile[worldWidth][worldHeight];
		random = new Random();

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
		final float scrollSpeed = 150f * dt;
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
		switch (interactionMode) {
			case Water: {
				if (Gdx.input.isTouched()) {
					droplets.add(new Droplet(mousePos.x, mousePos.y, 0f, -100f));
				}
			} break;
			case PlantSeed: {
				if (Gdx.input.justTouched()) {
					seeds.add(new Seed(mousePos.x, mousePos.y, PlantType.Leafy));
				}
			} break;
		}

		// Update droplets
		for (int i = 0; i < droplets.size; i++) {

			Droplet droplet = droplets.get(i);
			droplet.x += dt * droplet.dx;
			droplet.y += dt * droplet.dy;

			int tx = (int) (droplet.x / 16f),
				ty = (int) (droplet.y / 16f);
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

		// Update seeds
		for (int i = 0; i < seeds.size; i++) {

			Seed seed = seeds.get(i);
			seed.y += dt * seed.dy;

			int tx = (int) (seed.x / 16f),
				ty = (int) (seed.y / 16f);
			if ((tx < 0) || (tx >= worldWidth)
				|| (ty < 0) || (ty >= worldHeight)) {
				seeds.removeIndex(i);
			} else {
				// Seeds fall through the air
				Tile tile = tiles[tx][ty];
				if (tile.terrain == Terrain.Air) {
					seed.dy -= 98f * dt;
					if (seed.dy < -98f) seed.dy = -98f;
				} else {
					seed.dy = 0;

					// Prevent overlapping plants

					if ((tiles[tx][ty+1].plant == null)
						&& (random.nextFloat() > 0.99f)) {
						Plant newPlant = new Plant(seed.type, tx, ty + 1);
						plants.add(newPlant);
						tiles[tx][ty+1].plant = newPlant;
						seeds.removeIndex(i);
					}
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

		// Update plants
		for (int i=0; i<plants.size; i++) {
			Plant plant = plants.get(i);
			Tile groundTile = tiles[plant.x][plant.y-1];

			// Water
			plant.water -= 0.001f;
			float waterWanted = 1.0f - plant.water;
			if ((waterWanted > 0f) && (groundTile.humidity > 0f)) {
				float water = Math.min(waterWanted, groundTile.humidity) * 0.2f;
				groundTile.humidity -= water;
				plant.water += water;
			}

			if (plant.water <= 0) {
				plants.removeIndex(i);
				tiles[plant.x][plant.y].plant = null;
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
					setBatchColourLerped(colNoHumidity, colMaxHumidity, tile.humidity);
					batch.draw(texture, x * 16f, y * 16f);
				}
			}
		}

		// Draw plants
		for (Plant plant : plants) {
			setBatchColourLerped(colPlantDry, colPlantWet, plant.water);
			batch.draw(plant.type.plantTexture, plant.x * 16f, plant.y * 16f);
		}

		// Draw seeds
		batch.setColor(Color.WHITE);
		for (Seed seed : seeds) {
			batch.draw(seed.type.seedTexture, seed.x - 4f, seed.y - 4f);
		}
		// Draw droplets
		batch.setColor(Color.WHITE);
		for (Droplet droplet : droplets) {
			batch.draw(texDroplet, droplet.x - 4f, droplet.y - 4f);
		}

		// UI!
		uiMousePos.set(Gdx.input.getX(), Gdx.input.getY(), 0f);
		uiCamera.unproject(uiMousePos);
		batch.setProjectionMatrix(uiCamera.combined);
		int buttonSize = 48;
		int buttonX = 0;
		if (drawButton(buttonX, 0, buttonSize, buttonSize, texCloud, interactionMode == Water)) {
			interactionMode = Water;
		}
		buttonX += buttonSize;
		if (drawButton(buttonX, 0, buttonSize, buttonSize, PlantType.Leafy.seedTexture, interactionMode == InteractionMode.PlantSeed)) {
			interactionMode = InteractionMode.PlantSeed;
		}

		batch.end();

		mouseWasDown = Gdx.input.isTouched();
	}

	private void setBatchColourLerped(final Color minColour, final Color maxColour, float ratio) {
		// LibGDX is STUPID why does lerping a color edit the color??!?!??!?!?!?
		dumpColor.set(minColour);
		dumpColor.lerp(maxColour, ratio);
		batch.setColor(dumpColor);
	}

	private boolean drawButton(int x, int y, int w, int h, Texture image, boolean selected) {

		boolean activated = false;

		boolean over = (uiMousePos.x >= x) && (uiMousePos.x < x + w)
					&& (uiMousePos.y >= y) && (uiMousePos.y < y + h);
		boolean hit = false;

		if (over) {
			if (Gdx.input.isTouched()) {
				hit = true;
			} else if (mouseWasDown) {
				activated = true;
			}
		}

		if (hit) {
			buttonHitBackground.draw(batch, x, y, w, h);
		} else if (over || selected || activated) {
			buttonOverBackground.draw(batch, x, y, w, h);
		} else {
			buttonBackground.draw(batch, x, y, w, h);
		}
		batch.draw(image,
			x + (w - image.getWidth()) / 2,
			y + (h - image.getHeight()) / 2
		);

		return activated;
	}

	@Override
	public void resize(int width, int height) {
		super.resize(width, height);
		viewport.update(width, height);
		uiCamera.setToOrtho(false, width, height);
	}

	private void transferHumidity(Tile source, Tile dest) {
		if ((dest != null)
			&& (dest.terrain != Terrain.Air)) {

			float difference = source.humidity - dest.humidity;
			if (difference > 0) {
				float exchange = difference * 0.005f; // Tweak this to adjust speed of equalisation, bigger = faster
				source.humidity -= exchange;
				dest.humidity += exchange;
			}
		}
	}
}
