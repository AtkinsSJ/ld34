package uk.co.samatkins.ecosystem;

import com.badlogic.gdx.Application;
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

	enum Direction {
		Left,
		Right,
		Up,
		Down;
	}

	enum Terrain {
		Air(null, 1f),
		Soil(new Texture("soil.png"), 0.5f),
		Rock(new Texture("rock.png"), 0f),
		Water(new Texture("water.png"), 1f);

		final Texture texture;
		final float porosity;

		Terrain(Texture texture, float porosity) {
			this.texture = texture;
			this.porosity = porosity;
		}
	}

	class Tile {
		Terrain terrain = Terrain.Air;
		float humidity = 0;
		Plant plant = null;
	}

	enum InteractionMode {
		Water,
		PlantSeed,
		MakeSoil,
		MakeRock,
		Dig,
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
		Leafy(
			0.05f, 0.6f,
			3f, 3.5f, // Growth time range
			3, 5, // Min/max mature height
			new Texture("plant1_top.png"),
			new Texture[]{
				new Texture("plant1_1.png"),
				new Texture("plant1_2.png"),
			},
			new Texture("plant1_flower.png"),
			new Texture("seed1.png"), 5f
		);

		final float thirst; // Water consumed per second
		final float desiredSoilHumidity;
		final float minGrowthTime, maxGrowthTime;
		final int minMatureHeight, maxMatureHeight;
		final Texture texPlantTop;
		final Texture[] texPlant;
		final Texture texFlower;
		final Texture texSeed;
		final float seedLife;

		PlantType(float thirst, float desiredSoilHumidity,
		          float minGrowthTime, float maxGrowthTime, int minMatureHeight, int maxMatureHeight,
		          Texture texPlantTop, Texture[] texPlant, Texture texFlower,
		          Texture texSeed, float seedLife) {
			this.thirst = thirst;
			this.desiredSoilHumidity = desiredSoilHumidity;
			this.minMatureHeight = minMatureHeight;
			this.maxMatureHeight = maxMatureHeight;
			this.texPlantTop = texPlantTop;
			this.texPlant = texPlant;
			this.texSeed = texSeed;
			this.texFlower = texFlower;
			this.seedLife = seedLife;
			this.minGrowthTime = minGrowthTime;
			this.maxGrowthTime = maxGrowthTime;
		}
	}

	class Seed {
		PlantType type;
		float x, y;
		float dx, dy;
		float life;

		public Seed(float x, float y, PlantType type) {
			this.x = x;
			this.y = y;
			this.type = type;

			this.dx = this.dy = 0;
			this.life = type.seedLife;
		}
	}

	class Plant {
		PlantType type;
		int x, y; // Base

		int size;
		int matureHeight;
		float health;

		float growthTimer;
		float water;
		boolean isMature;

		public Plant(PlantType type, int x, int y) {
			this.type = type;
			this.x = x;
			this.y = y;

			this.water = 0.5f;
			this.health = 1.0f;

			this.size = 1;
			this.matureHeight = randomInt(random, this.type.minMatureHeight, this.type.maxMatureHeight + 1);
			this.isMature = false;
			this.growthTimer = randomFloat(random, type.minGrowthTime, type.maxGrowthTime);
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
	Texture texCloud, texSpade;

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
	public static float randomFloat(Random random, float min, float max) {
		return min + (random.nextFloat() * (max - min));
	}
	public static void log(String string) {
		Gdx.app.debug("Whatever", string);
	}
	
	@Override
	public void create () {
		Gdx.app.setLogLevel(Application.LOG_DEBUG);

		batch = new SpriteBatch();
		camera = new OrthographicCamera();
		viewport = new ScreenViewport(camera);
		uiCamera = new OrthographicCamera();

		texDroplet = new Texture("raindrop.png");
		texCloud = new Texture("cloud.png");
		texSpade = new Texture("spade.png");
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
					if (random.nextFloat() > 0.7f) {
						tile.terrain = Terrain.Rock;
					} else {
						tile.terrain = Terrain.Soil;
						tile.humidity = random.nextFloat();
					}

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

		// Interactions!
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
			case MakeSoil: {
				if (Gdx.input.isTouched()) {
					int tx = (int) (mousePos.x / 16f),
						ty = (int) (mousePos.y / 16f);
					if (tx >=0 && tx < worldWidth && ty >= 0 && ty < worldHeight) {
						tiles[tx][ty].terrain = Terrain.Soil;
					}
				}
			} break;
			case MakeRock: {
				if (Gdx.input.isTouched()) {
					int tx = (int) (mousePos.x / 16f),
						ty = (int) (mousePos.y / 16f);
					if (tx >=0 && tx < worldWidth && ty >= 0 && ty < worldHeight) {
						tiles[tx][ty].terrain = Terrain.Rock;
					}
				}
			} break;
			case Dig: {
				if (Gdx.input.isTouched()) {
					int tx = (int) (mousePos.x / 16f),
						ty = (int) (mousePos.y / 16f);
					if (tx >=0 && tx < worldWidth && ty >= 0 && ty < worldHeight) {
						Tile tile = tiles[tx][ty];
						if (tile.humidity > 0f) {
							tile.terrain = Terrain.Water;
						} else {
							tile.terrain = Terrain.Air;
						}
					}
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

					if (tile.terrain.porosity > 0.0f) {
						tile.humidity += 0.1f;
					} else {
						 // Create a puddle!
						Tile waterTile = tiles[tx][ty + 1];
						waterTile.terrain = Terrain.Water;
						waterTile.humidity = 0.1f;
					}
					droplets.removeIndex(i);
				}
			}
		}

		// Update seeds
		for (int i = 0; i < seeds.size; i++) {

			Seed seed = seeds.get(i);

			seed.x += dt * seed.dx;
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
					seed.dx = 0;
					seed.dy = 0;
					seed.life -= dt;

					// Die if lain around too long, or 'suffocated'
					if ((seed.life < 0f)
					 || (tiles[tx][ty + 1].terrain != Terrain.Air)){
						seeds.removeIndex(i);
					} else {
						// Randomly grow into a plant if there's room
						if ((tiles[tx][ty + 1].plant == null)
							&& (random.nextFloat() > 0.99f)) {
							Plant newPlant = new Plant(seed.type, tx, ty + 1);
							plants.add(newPlant);
							tiles[tx][ty + 1].plant = newPlant;
							seeds.removeIndex(i);
						}
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
					if ((above != null) && (above.terrain == Terrain.Air)) {
						tile.humidity *= 0.999f;
					}

					// Osmosis and puddle spread
					transferHumidity(tile, above, Direction.Up);
					transferHumidity(tile, below, Direction.Down);
					transferHumidity(tile, left,  Direction.Left);
					transferHumidity(tile, right, Direction.Right);

					if (tile.humidity <= 0.01f) {
						tile.humidity = 0f;
						if (tile.terrain == Terrain.Water) {
							tile.terrain = Terrain.Air;
						}
					}
				}
			}
		}

		// Update plants
		for (int i=0; i<plants.size; i++) {
			if (updatePlant(plants.get(i), dt)) {
				plants.removeIndex(i);
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

				switch (tile.terrain) {
					case Water: {
//						batch.setColor(Color.RED);
//						batch.draw(tile.terrain.texture, x*16f, y*16f, 16f, 16f);
						batch.setColor(Color.WHITE);
						batch.draw(tile.terrain.texture, x*16f, y*16f, 16f, tile.humidity * 16f);
					} break;

					default: {
						Texture texture = tile.terrain.texture;
						if (texture != null) {
							setBatchColourLerped(colNoHumidity, colMaxHumidity, tile.humidity);
							batch.draw(texture, x * 16f, y * 16f);
						}
					} break;
				}
			}
		}

		// Draw plants
		for (Plant plant : plants) {
			setBatchColourLerped(colPlantDry, colPlantWet, plant.health);
			for (int i=0; i<plant.size - 1; i++) {
				batch.draw(plant.type.texPlant[i % plant.type.texPlant.length], plant.x * 16f, (plant.y + i) * 16f);
			}
			batch.draw(plant.type.texPlantTop, plant.x * 16f, (plant.y + plant.size - 1) * 16f);
			if (plant.isMature) {
				batch.setColor(Color.WHITE);
				batch.draw(plant.type.texFlower, plant.x * 16f, (plant.y + plant.size - 1) * 16f);
			}
		}

		// Draw seeds
		batch.setColor(Color.WHITE);
		for (Seed seed : seeds) {
			batch.draw(seed.type.texSeed, seed.x - 4f, seed.y - 4f);
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
		if (drawButton(buttonX, 0, buttonSize, buttonSize, PlantType.Leafy.texSeed, interactionMode == InteractionMode.PlantSeed)) {
			interactionMode = InteractionMode.PlantSeed;
		}
		buttonX += buttonSize;
		if (drawButton(buttonX, 0, buttonSize, buttonSize, Terrain.Soil.texture, interactionMode == InteractionMode.MakeSoil)) {
			interactionMode = InteractionMode.MakeSoil;
		}
		buttonX += buttonSize;
		if (drawButton(buttonX, 0, buttonSize, buttonSize, Terrain.Rock.texture, interactionMode == InteractionMode.MakeRock)) {
			interactionMode = InteractionMode.MakeRock;
		}
		buttonX += buttonSize;
		if (drawButton(buttonX, 0, buttonSize, buttonSize, texSpade, interactionMode == InteractionMode.Dig)) {
			interactionMode = InteractionMode.Dig;
		}

		batch.end();

		mouseWasDown = Gdx.input.isTouched();
	}

	private void transferHumidity(Tile source, Tile dest, Direction direction) {
		if (dest != null) {

			float difference = source.humidity - dest.humidity;
			boolean doExchange = true;
			float exchange = 0f;

			switch (source.terrain) {
				case Air: {
					doExchange = false; // Air cannot transfer
				} break;

				case Water: {
					doExchange = (direction != Direction.Up)
						&& ((direction == Direction.Down) || (difference > 0f));
					if (doExchange) {
						if (direction == Direction.Down) {
							exchange = Math.min(source.humidity, 1.0f - dest.humidity) * dest.terrain.porosity;
						} else {
							exchange = difference * 0.5f * 0.2f * dest.terrain.porosity;
						}
					}
				} break;

				default: {
					doExchange = (difference > 0.0f )
						&& (dest.terrain != Terrain.Air)
						&& (dest.terrain != Terrain.Water);
					if (doExchange) {
						exchange = difference * 0.02f * dest.terrain.porosity;
					}
				}
			}

			if (doExchange) {
				source.humidity -= exchange;
				dest.humidity += exchange;

				if ((dest.terrain == Terrain.Air) && (dest.humidity > 0.0f)) {
					dest.terrain = Terrain.Water;
				}
			}
		}
	}

	private boolean updatePlant(Plant plant, float dt) {
		boolean plantDied = false;

		Tile groundTile = tiles[plant.x][plant.y-1];

		// Water
		//plant.water -= dt * (plant.type.thirst * (1.0f + plant.size * 0.1f));
		//log("Plant water is " + plant.water);
		float waterWanted = plant.type.desiredSoilHumidity - plant.water;
		if ((waterWanted > 0f) && (groundTile.humidity > 0f)) {
			log("Plant drank " + waterWanted);
			float water = Math.min(waterWanted, groundTile.humidity) * dt;
			groundTile.humidity -= water;
			plant.water += water;
		}

		float humidityDifference = Math.abs(groundTile.humidity - plant.type.desiredSoilHumidity);
		log("Soil humidity = " + groundTile.humidity + ", Humidity difference is " + humidityDifference);
		if (humidityDifference < 0.15f) {
			// Happy
			plant.health = Math.min(1.0f, plant.health + dt);

			if (plant.health >= 0.99f) {
				plant.growthTimer -= dt;

				if (plant.growthTimer <= 0f) {
					plant.growthTimer = randomFloat(random, plant.type.minGrowthTime, plant.type.maxGrowthTime);

					plant.water -= 0.1f;
					if (plant.isMature) {
						// Spawn seeds!
						Seed seed = new Seed((plant.x + 0.5f) * 16f, (plant.y + plant.size + 0.5f) * 16f, plant.type);
						seed.dx = (random.nextFloat() - 0.5f) * 50f;
						seed.dy = 20f + (random.nextFloat() * 20f);
						seeds.add(seed);

					} else if (plant.size >= plant.matureHeight) {
						plant.isMature = true;
						plant.size = plant.matureHeight;
					} else {
						plant.size++;

						// Slightly hacky!
						// This way, plants can start immature and then grow to maturity, even if their mature height is just 1
						if (plant.size >= plant.matureHeight) {
							plant.isMature = true;
							plant.size = plant.matureHeight;
						}
					}
				}
			}

		} else if (humidityDifference < 0.4f) {
			// Unhappy but ok
		} else {
			// Dying
			plant.health -= (dt * 0.1f);
			log("Dying, health = " + plant.health);
		}

		if (plant.health <= 0f) {
			plantDied = true;
			tiles[plant.x][plant.y].plant = null;
		}

		return plantDied;
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
}
