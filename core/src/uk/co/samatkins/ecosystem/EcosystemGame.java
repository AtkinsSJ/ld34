package uk.co.samatkins.ecosystem;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.NinePatch;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.XmlReader;
import com.badlogic.gdx.utils.XmlWriter;
import com.badlogic.gdx.utils.viewport.ScreenViewport;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.Random;

public class EcosystemGame extends ApplicationAdapter {

	public static final String SAVE_FILENAME = "ecosystem.xml";
	private Random random;
	public static final int buttonSize = 48;

	enum Direction {
		Left,
		Right,
		Up,
		Down;
	}

	enum Terrain {
		Air(null, 1f, false, false),
		Soil(new Texture("soil.png"), 0.5f, true, false),
		Rock(new Texture("rock.png"), 0f, true, false),
		Water(new Texture("water.png"), 1f, false, true),
		Spring(new Texture("water.png"), 1f, false, true);

		final Texture texture;
		final float porosity;
		final boolean isSolid;
		final boolean isWater;

		Terrain(Texture texture, float porosity, boolean isSolid, boolean isWater) {
			this.texture = texture;
			this.porosity = porosity;
			this.isSolid = isSolid;
			this.isWater = isWater;
		}
	}

	class Tile {
		final int x, y;
		Terrain terrain = Terrain.Air;
		float humidity = 0;
		Plant plant = null;

		public Tile(int x, int y) {

			this.x = x;
			this.y = y;
		}
	}

	enum InteractionMode {
		Water(0.05f),
		MakeSpring(0f),
		PlantSeed(0.2f),
		MakeSoil(0f),
		MakeRock(0f),
		Dig(0f);

		final float delay;

		InteractionMode(float delay) {
			this.delay = delay;
		}
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
			false, 0.1f, 0.4f,
			3f, 3.5f, // Growth time range
			3, 5, // Min/max mature height
			new Texture("plant1_top.png"),
			new Texture[]{
				new Texture("plant1_1.png"),
				new Texture("plant1_2.png"),
			},
			new Texture("plant1_flower.png"),
			new Texture("seed1.png"), 10f,
			1f
		),
		Lilypad(
			true, 0.1f, 0.7f,
			10f, 15f,
			1, 1,
			new Texture("plant2_top.png"),
			new Texture[]{},
			new Texture("plant2_flower.png"),
			new Texture("plant2_seed.png"), 10f,
			1.25f
		),
		Cactus(
			false, 0.01f, 0.1f,
			10f, 15f,
			1, 3,
			new Texture("plant3_top.png"),
			new Texture[]{
				new Texture("plant3_1.png"),
			},
			new Texture("plant3_flower.png"),
			new Texture("plant3_seed.png"), 10f,
			0.75f
		);

		final boolean isAquatic;
		final float thirst; // Water consumed per second
		final float desiredSoilHumidity;
		final float minGrowthTime, maxGrowthTime;
		final int minMatureHeight, maxMatureHeight;
		final Texture texPlantTop;
		final Texture[] texPlant;
		final Texture texFlower;
		final Texture texSeed;
		final float seedLife;
		final float audioPitch;

		PlantType(boolean isAquatic, float thirst, float desiredSoilHumidity,
		          float minGrowthTime, float maxGrowthTime, int minMatureHeight, int maxMatureHeight,
		          Texture texPlantTop, Texture[] texPlant, Texture texFlower,
		          Texture texSeed, float seedLife,
		          float audioPitch) {
			this.isAquatic = isAquatic;
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
			this.audioPitch = audioPitch;
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
		float x, y; // Base

		float health;
		float water;

		int size;
		int matureHeight;

		float growthTimer;
		boolean isMature;

		public Plant(PlantType type, float x, float y) {
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
	InteractionMode interactionMode = InteractionMode.Water;
	float interactionCooldown = 0f;
	PlantType seedType;

	NinePatch buttonBackground, buttonOverBackground, buttonHitBackground;
	Texture texCloud, texSpade, texSpring, texSave, texLoad, texSound, texRegenerate;
	Sound sndDie, sndDroplet, sndGrow, sndSeed, sndWater;
	private boolean audioEnabled = true;

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
		camera = new OrthographicCamera(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
		viewport = new ScreenViewport(camera);
		uiCamera = new OrthographicCamera();

		texDroplet = new Texture("raindrop.png");
		texCloud = new Texture("cloud.png");
		texSpade = new Texture("spade.png");
		texSpring = new Texture("spring.png");
		texSave = new Texture("save.png");
		texLoad = new Texture("load.png");
		texSound = new Texture("sound.png");
		texRegenerate = new Texture("regenerate.png");
		buttonBackground = new NinePatch(new Texture("button.png"), 6, 6, 6, 6);
		buttonOverBackground = new NinePatch(new Texture("button-over.png"), 6, 6, 6, 6);
		buttonHitBackground = new NinePatch(new Texture("button-hit.png"), 6, 6, 6, 6);

		sndDie = Gdx.audio.newSound(Gdx.files.internal("die.mp3"));
		sndDroplet = Gdx.audio.newSound(Gdx.files.internal("droplet.mp3"));
		sndGrow = Gdx.audio.newSound(Gdx.files.internal("grow.mp3"));
		sndSeed = Gdx.audio.newSound(Gdx.files.internal("seed.mp3"));
		sndWater = Gdx.audio.newSound(Gdx.files.internal("water.mp3"));

		generateWorld();

		camera.position.set(
			((worldWidth * 16f) - camera.viewportWidth) / 2f,
			((worldHeight * 16f) - camera.viewportHeight) / 2f,
			0f
		);
		camera.update();
	}

	private void generateWorld() {
		// Really crummy terrain generation
		droplets.clear();
		seeds.clear();
		plants.clear();

		worldWidth = 80;
		worldHeight = 40;
		tiles = new Tile[worldWidth][worldHeight];
		random = new Random();

		int depth = randomInt(random, 4, 15);

		for (int x = 0; x < worldWidth; x++) {

			depth = randomInt(random, Math.max(1, depth - 2), Math.min(15, depth + 3));

			for (int y = 0; y < worldHeight; y++) {
				Tile tile = new Tile(x, y);
				if (y == depth) {
					tile.terrain = Terrain.Water;
					tile.humidity = random.nextFloat();
				} else if (y < depth) {
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

		// Scatter some random seeds
		for (PlantType plantType : PlantType.values()) {
			int count = randomInt(random, 3, 10);
			for (int i = 0; i < count; i++) {
				newSeed(
					plantType,
					randomFloat(random, 0.5f, worldWidth - 0.5f),
					randomFloat(random, 0.5f, worldHeight - 0.5f),
					randomFloat(random, -25f, 25f),
					randomFloat(random, 20f, 40f)
				);
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
		uiMousePos.set(Gdx.input.getX(), Gdx.input.getY(), 0f);
		uiCamera.unproject(uiMousePos);

		// Interactions!
		if (Gdx.input.isTouched() && (uiMousePos.y > buttonSize)) {
			interactionCooldown -= dt;

			if (interactionCooldown <= 0) {
				interactionCooldown = interactionMode.delay;

				switch (interactionMode) {
					case Water: {
						droplets.add(new Droplet(mousePos.x, mousePos.y, 0f, -100f));
					} break;
					case PlantSeed: {
						seeds.add(new Seed(mousePos.x, mousePos.y, seedType));
					} break;
					case MakeSpring: {
						int tx = (int) (mousePos.x / 16f),
							ty = (int) (mousePos.y / 16f);
						if (tx >=0 && tx < worldWidth && ty >= 0 && ty < worldHeight) {
							tiles[tx][ty].terrain = Terrain.Spring;
						}
					} break;
					case MakeSoil: {
						int tx = (int) (mousePos.x / 16f),
							ty = (int) (mousePos.y / 16f);
						if (tx >=0 && tx < worldWidth && ty >= 0 && ty < worldHeight) {
							tiles[tx][ty].terrain = Terrain.Soil;
						}
					} break;
					case MakeRock: {
						int tx = (int) (mousePos.x / 16f),
							ty = (int) (mousePos.y / 16f);
						if (tx >=0 && tx < worldWidth && ty >= 0 && ty < worldHeight) {
							tiles[tx][ty].terrain = Terrain.Rock;
						}
					} break;
					case Dig: {
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
					} break;
				}
			}
		} else {
			interactionCooldown = 0;
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

					float water = 0.1f;

					if ((tile.terrain.porosity > 0.0f)
					 && (tile.humidity < 1.0f)){
						float waterAbsorbed = Math.min(1.0f - tile.humidity, water);
						water -= waterAbsorbed;
						modifyHumidity(tile, waterAbsorbed);
					}

					if (water > 0.0f) {
						 // Create a puddle!
						Tile waterTile = tiles[tx][ty + 1];
						modifyHumidity(waterTile, water);
					}

					playSound(sndDroplet);
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
				} else if (tile.terrain.isWater) {
					// Seeds float on water
					if (seed.dy < -1f) {
						playSound(sndWater);
					}
					seed.dy = 0;
					seed.y = getTopOfWater(tx, ty) * 16f;
				} else {
					seed.dx = 0;
					seed.dy = 0;
				}

				seed.life -= dt;

				// Die if lain around too long, or 'suffocated'
				if ((seed.life < 0f)
					|| (tiles[tx][ty + 1].terrain.isSolid)) {
					seeds.removeIndex(i);

					// Randomly grow into a plant if there's room
				} else {

					boolean canGrowHere;

					Tile targetTile;
					if (seed.type.isAquatic) {
						canGrowHere = tile.terrain.isWater;
						targetTile = tiles[tx][ty];
					} else {
						canGrowHere = tile.terrain.isSolid
							|| (tile.terrain.isWater && tile.humidity < 0.2f);
						targetTile = tiles[tx][ty + 1];
					}

					if (targetTile.plant == null) {

						if (canGrowHere
							&& (random.nextFloat() > 0.99f)) {
							Plant newPlant = new Plant(seed.type, tx, ty + 1);
							plants.add(newPlant);
							targetTile.plant = newPlant;
							seeds.removeIndex(i);
							playSound(sndGrow, newPlant.type.audioPitch);
						}
					}
				}
			}
		}

		// Update humidity
		for (int x = 0; x < worldWidth; x++) {
			for (int y = 0; y < worldHeight; y++) {
				Tile tile = tiles[x][y];

				if (tile.terrain == Terrain.Spring) {
					modifyHumidity(tile, 1f * dt); // Spring strength
				}

				if (tile.terrain != Terrain.Air) { // TODO: Air humidity???

					Tile above = (y < (worldHeight-1)) ? tiles[x][y + 1] : null;
					Tile below = (y > 0) ? tiles[x][y - 1] : null;
					Tile left = (x > 0) ? tiles[x-1][y] : null;
					Tile right = (x < (worldWidth - 1)) ? tiles[x+1][y] : null;

					// Evaporation
					if ((above != null) && (above.terrain == Terrain.Air)) {
						modifyHumidity(tile, -tile.humidity * 0.001f);
					}

					// Osmosis and puddle spread
					transferHumidity(tile, above, Direction.Up);
					transferHumidity(tile, below, Direction.Down);
					transferHumidity(tile, left,  Direction.Left);
					transferHumidity(tile, right, Direction.Right);
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

//				if (tile.plant != null) {
//					batch.setColor(Color.YELLOW);
//					batch.draw(Terrain.Water.texture, x*16f, y*16f, 16f, 16f);
//				}

				if (tile.terrain.isWater) {
//					batch.setColor(Color.RED);
//					batch.draw(tile.terrain.texture, x*16f, y*16f, 16f, 16f);
					if (tile.terrain == Terrain.Spring) {
						batch.setColor(0f, 0f, 1f, 0.8f);
					} else {
						batch.setColor(1f, 1f, 1f, 0.8f);
					}
					batch.draw(tile.terrain.texture, x*16f, y*16f, 16f, tile.humidity * 16f);
				} else {
					Texture texture = tile.terrain.texture;
					if (texture != null) {
						setBatchColourLerped(colNoHumidity, colMaxHumidity, tile.humidity);
						batch.draw(texture, x * 16f, y * 16f);
					}
				}
			}
		}

		// Draw plants
		for (Plant plant : plants) {
			batch.setColor(Color.YELLOW);

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
		batch.setProjectionMatrix(uiCamera.combined);
		int buttonX = 0;
		if (drawButton(buttonX, 0, buttonSize, buttonSize, texCloud, interactionMode == InteractionMode.Water)) {
			interactionMode = InteractionMode.Water;
		}
		buttonX += buttonSize;
		if (drawButton(buttonX, 0, buttonSize, buttonSize, texSpring, interactionMode == InteractionMode.MakeSpring)) {
			interactionMode = InteractionMode.MakeSpring;
		}
		for (PlantType plantType : PlantType.values()) {
			buttonX += buttonSize;
			if (drawButton(buttonX, 0, buttonSize, buttonSize, plantType.texSeed,
				(interactionMode == InteractionMode.PlantSeed) && (seedType == plantType))) {
				interactionMode = InteractionMode.PlantSeed;
				seedType = plantType;
			}
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

		buttonX = (int) uiCamera.viewportWidth;
		buttonX -= buttonSize;
		if (drawButton(buttonX, 0, buttonSize, buttonSize, texLoad, false)) {
			// Load!
			loadGame();
		}
		buttonX -= buttonSize;
		if (drawButton(buttonX, 0, buttonSize, buttonSize, texSave, false)) {
			// Save!
			saveGame();
		}
		buttonX -= buttonSize;
		if (drawButton(buttonX, 0, buttonSize, buttonSize, texRegenerate, false)) {
			generateWorld();
		}
		buttonX -= buttonSize;
		if (drawButton(buttonX, 0, buttonSize, buttonSize, texSound, audioEnabled)) {
			audioEnabled = !audioEnabled;
		}

		batch.end();

		mouseWasDown = Gdx.input.isTouched();
	}

	private void saveGame() {
		if (!Gdx.files.isLocalStorageAvailable()) {
			log("Couldn't access storage");
			return;
		}

		try {
			FileHandle saveFile = Gdx.files.local(SAVE_FILENAME);
			Writer writer = saveFile.writer(false);
			XmlWriter xml = new XmlWriter(writer);
			xml.element("ecosystem")
				.attribute("width", worldWidth)
				.attribute("height", worldHeight);
			{
				xml.element("tiles");
				for (int y=0; y<worldHeight; y++) {
					for (int x = 0; x < worldWidth; x++) {
						Tile t = tiles[x][y];
						xml.element("t")
							.attribute("x", x)
							.attribute("y", y)
							.attribute("terrain", t.terrain.name())
							.attribute("humidity", t.humidity)
							.pop();
					}
				}
				xml.pop();

				xml.element("plants");
				{
					for (Plant plant : plants) {
						xml.element("plant")
							.attribute("type", plant.type.name())
							.attribute("x", plant.x)
							.attribute("y", plant.y)
							.attribute("health", plant.health)
							.attribute("water", plant.water)
							.attribute("size", plant.size)
							.attribute("matureHeight", plant.matureHeight)
							.attribute("isMature", plant.isMature)
							.attribute("growthTimer", plant.growthTimer)
							.pop();
					}
				}
				xml.pop();

				xml.element("seeds");
				{
					for (Seed seed : seeds) {
						xml.element("seed")
							.attribute("type", seed.type.name())
							.attribute("x", seed.x)
							.attribute("y", seed.y)
							.attribute("dx", seed.dx)
							.attribute("dy", seed.dy)
							.attribute("life", seed.life)
							.pop();
					}
				}
				xml.pop();

				xml.element("droplets");
				{
					for (Droplet droplet : droplets) {
						xml.element("droplet")
							.attribute("x", droplet.x)
							.attribute("y", droplet.y)
							.attribute("dx", droplet.dx)
							.attribute("dy", droplet.dy)
							.pop();
					}
				}
				xml.pop();
			}
			xml.pop();
			writer.close();

			log("Game saved to " + saveFile.file().getAbsolutePath());
		} catch (IOException e) {
			e.printStackTrace();
			log("Failed to save, with an error.");
		}
	}

	private void loadGame() {
		if (!Gdx.files.isLocalStorageAvailable()) {
			log("Couldn't access storage");
			return;
		}
		try {
			FileHandle saveFile = Gdx.files.local(SAVE_FILENAME);
			Reader reader = saveFile.reader();

			XmlReader xmlReader = new XmlReader();
			XmlReader.Element xml = xmlReader.parse(reader);

			droplets.clear();
			seeds.clear();
			plants.clear();
			worldWidth = xml.getIntAttribute("width", 80);
			worldHeight = xml.getIntAttribute("height", 40);
			tiles = new Tile[worldWidth][worldHeight];

			XmlReader.Element xmlTiles = xml.getChildByName("tiles");
			for (int i=0; i<xmlTiles.getChildCount(); i++) {
				XmlReader.Element xmlTile = xmlTiles.getChild(i);
				Tile tile = new Tile(xmlTile.getIntAttribute("x"), xmlTile.getIntAttribute("y"));
				tile.terrain = Terrain.valueOf(xmlTile.getAttribute("terrain", Terrain.Air.name()));
				tile.humidity = xmlTile.getFloatAttribute("humidity", 0f);

				tiles[tile.x][tile.y] = tile;
			}

			XmlReader.Element xmlPlants = xml.getChildByName("plants");
			for (int i=0; i<xmlPlants.getChildCount(); i++) {
				XmlReader.Element xmlPlant = xmlPlants.getChild(i);
				Plant plant = new Plant(
					PlantType.valueOf(xmlPlant.getAttribute("type", PlantType.Leafy.name())),
					xmlPlant.getFloatAttribute("x"),
					xmlPlant.getFloatAttribute("y")
				);
				plant.health = xmlPlant.getFloatAttribute("health", 1f);
				plant.water = xmlPlant.getFloatAttribute("water", 1f);
				plant.size = xmlPlant.getIntAttribute("size", 1);
				plant.matureHeight = xmlPlant.getIntAttribute("matureHeight", 1);
				plant.isMature = xmlPlant.getBooleanAttribute("isMature", false);
				plant.growthTimer = xmlPlant.getFloatAttribute("growthTimer", 1f);

				plants.add(plant);
			}

			XmlReader.Element xmlSeeds = xml.getChildByName("seeds");
			for (int i=0; i<xmlSeeds.getChildCount(); i++) {
				XmlReader.Element x = xmlSeeds.getChild(i);
				Seed seed = new Seed(
					x.getFloatAttribute("x"),
					x.getFloatAttribute("y"),
					PlantType.valueOf(x.getAttribute("type", PlantType.Leafy.name()))
				);
				seed.dx = x.getFloatAttribute("dx", 0f);
				seed.dy = x.getFloatAttribute("dy", 0f);
				seed.life = x.getFloatAttribute("life", 1f);

				seeds.add(seed);
			}

			XmlReader.Element xmlDroplets = xml.getChildByName("droplets");
			for (int i=0; i<xmlDroplets.getChildCount(); i++) {
				XmlReader.Element x = xmlDroplets.getChild(i);
				Droplet droplet = new Droplet(
					x.getFloatAttribute("x"),
					x.getFloatAttribute("y"),
					x.getFloatAttribute("dx", 0f),
					x.getFloatAttribute("dy", 0f)
				);

				droplets.add(droplet);
			}

			reader.close();
			log("Game loaded from " + saveFile.file().getAbsolutePath());
		} catch (IOException e) {
			e.printStackTrace();
			log("Failed to load, with an error.");
		}
	}

	// Calculates where the top of the water is, starting in the given tile and looking up and down
	private float getTopOfWater(int tileX, int tileY) {

		// Find the bottom water tile
		int y = tileY;
		while ((y > 0) && (tiles[tileX][y-1].terrain.isWater)) {
			y--;
		}

		if (tiles[tileX][y].terrain.isWater) {
			// Climb upwards until there's no more water
			while ((y < worldHeight-1) && (tiles[tileX][y].humidity > 0.95f) && (tiles[tileX][y+1].terrain.isWater)) {
				y++;
			}
			return y + tiles[tileX][y].humidity;

		} else {
			// Lowest tile isn't water, so we just sit here.
			return y;
		}
	}

	private void modifyHumidity(Tile tile, float dHumidity) {
		tile.humidity += dHumidity;
		if ((tile.terrain == Terrain.Air) && (tile.humidity > 0.0f)) {
			tile.terrain = Terrain.Water;
		} else if ((tile.terrain == Terrain.Water) && (tile.humidity < 0.001f)) {
			tile.terrain = Terrain.Air;
		}

		if (tile.humidity > 1.0f) {
			// Move some humidity upwards
			if (tile.y < worldHeight-1) {
				float water = tile.humidity - 1.0f;
				modifyHumidity(tiles[tile.x][tile.y+1], water);
				tile.humidity = 1.0f;
			}
		}
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

				case Spring:
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
						&& (!dest.terrain.isWater);
					if (doExchange) {
						exchange = difference * 0.02f * dest.terrain.porosity;
					}
				}
			}

			if (doExchange) {
				modifyHumidity(source, -exchange);
				modifyHumidity(dest, exchange);
			}
		}
	}

	private boolean updatePlant(Plant plant, float dt) {
		boolean plantDied = false;

		int tx = (int)plant.x,
			ty = (int)plant.y;

		Tile groundTile;

		if (plant.type.isAquatic) {

			// Move up or down so we're on the surface of the water
			float newY = getTopOfWater(tx, ty);
			int newTY = (int)newY;
			if (newTY != ty) {
				tiles[tx][ty].plant = null;
				tiles[tx][newTY].plant = plant;
			}
			plant.y = newY;
			groundTile = tiles[tx][newTY];

		} else {
			groundTile = tiles[tx][ty-1];
			if (!groundTile.terrain.isSolid) {
				plantDied = true;
			}
		}

		if (!plantDied) {

			// Water
			plant.water -= dt * (plant.type.thirst * plant.size);
			if (plant.water < plant.type.desiredSoilHumidity) {
				float waterWanted = plant.type.desiredSoilHumidity - plant.water;
				if ((waterWanted > 0f) && (groundTile.humidity > 0f)) {
					float water = Math.min(waterWanted, groundTile.humidity) * dt;
					modifyHumidity(groundTile, -water);
					plant.water += water;
				}
			}

			float humidityDifference = Math.abs(groundTile.humidity - plant.type.desiredSoilHumidity);

			if (plant.type.isAquatic) {
				humidityDifference = (groundTile.terrain.isWater)
					? 0f
					: 0.8f;
			}

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
							newSeed(plant.type,
								(plant.x + 0.5f),
								(plant.y + plant.size + 0.5f),
								randomFloat(random, -25f, 25f),
								randomFloat(random, 20f, 40f)
							);
							playSound(sndSeed, plant.type.audioPitch);

						} else if (plant.size >= plant.matureHeight) {
							plant.isMature = true;
							plant.size = plant.matureHeight;
						} else {
							plant.size++;
							playSound(sndGrow, plant.type.audioPitch);

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
			}

			if (plant.health <= 0f) {
				plantDied = true;
			}
		}

		if (plantDied) {
			playSound(sndDie, plant.type.audioPitch);
			tiles[(int) plant.x][(int) plant.y].plant = null;
		}

		return plantDied;
	}

	private void newSeed(PlantType type, float x, float y, float dx, float dy) {
		Seed seed = new Seed(x * 16f, y * 16f, type);
		seed.dx = dx;
		seed.dy = dy;
		seeds.add(seed);
	}

	private void playSound(Sound sound) {
		playSound(sound, 1f);
	}
	private void playSound(Sound sound, float pitch) {
		if (audioEnabled) {
			sound.play(1f, pitch, 0f);
		}
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
		viewport.update(width, height, true);
		uiCamera.setToOrtho(false, width, height);
	}
}
