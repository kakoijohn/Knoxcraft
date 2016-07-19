package org.knoxcraft.serverturtle;

import java.util.Map;

import org.knoxcraft.turtle3d.KCTBlockTypes;
import org.knoxcraft.turtle3d.KCTBlockTypesBuilder;
import org.knoxcraft.turtle3d.KCTCommand;
import org.knoxcraft.turtle3d.KCTScript;
import org.knoxcraft.turtle3d.TurtleCommandException;
import org.knoxcraft.turtle3d.TurtleDirection;
import org.slf4j.Logger;
import org.spongepowered.api.block.BlockState;
import org.spongepowered.api.world.World;

import com.flowpowered.math.vector.Vector3i;

public class SpongeTurtle {

	// @Inject
	private Logger log;

	// need to know initial location
	// vector 3i, or XYZ, get location from turtle plugin, put turtle one block
	// oin front of player.
	/*
	 * EXAMPLE CODE Vector3i pos=loc.getBlockPosition(); int x=pos.getX(); int
	 * z=pos.getZ();
	 */
	// turtle location:relPos
	private Vector3i curLoc;
	// player location:originPos
	private Vector3i playerLoc;
	private TurtleDirection dir;
	private World world;
	private BlockState bt = KCTBlockTypesBuilder.getBlockState(KCTBlockTypes.STONE);

	public SpongeTurtle(Logger logger) {
		this.log = logger;
	}

	public void setLoc(Vector3i curLoc) {
		this.curLoc = curLoc;
	}

	public void setTurtleDirection(TurtleDirection d) {
		this.dir = d;
	}

	public void setWorld(World w) {
		this.world = w;
	}

	private static int toInt(Object o) {
		if (o instanceof Long) {
			return (int) ((Long) o).longValue();
		} else {
			return (Integer) o;
		}
	}

	private TurtleDirection TurtleDirectionInt(int TurtleDirectionInt) {

		while (TurtleDirectionInt < 0) {
			TurtleDirectionInt += 8;
		}

		if (TurtleDirectionInt == 0 || TurtleDirectionInt == 8) {
			return TurtleDirection.NORTH;

		} else if (TurtleDirectionInt == 1) {
			return TurtleDirection.NORTHEAST;

		} else if (TurtleDirectionInt == 2) {
			return TurtleDirection.EAST;

		} else if (TurtleDirectionInt == 3) {
			return TurtleDirection.SOUTHEAST;

		} else if (TurtleDirectionInt == 4) {
			return TurtleDirection.SOUTH;

		} else if (TurtleDirectionInt == 5) {
			return TurtleDirection.SOUTHWEST;

		} else if (TurtleDirectionInt == 6) {
			return TurtleDirection.WEST;

		} else if (TurtleDirectionInt == 7) {
			return TurtleDirection.NORTHWEST;

		} else {
			throw new RuntimeException("TurtleDirectionINT invalid=" + TurtleDirectionInt);
		}
	}

	private void turn(boolean left, int degrees) {

		// get current TurtleDirection
		double d = degrees / 360.0 * 8;
		int x = (int) Math.round(d);
		int currentDir = dir.getIntValue();

		if (left) {
			int newDir = currentDir - x;
			while (newDir < 0) {
				newDir += 8;
			}
			dir = TurtleDirection.valueOf(newDir);
		} else {
			int newdir = currentDir + x;
			newdir = newdir % 8;
			dir = TurtleDirection.valueOf(newdir);
		}
	}

	private void move(int distance, TurtleDirection turtleDirection) {

		int x = curLoc.getX();
		int y = curLoc.getY();
		int z = curLoc.getZ();

		log.info("current location: " + x + ", " + y + ", " + z);

		for (int i = 1; i <= distance; i++) {
			/*
			 * Sponge does this backwards for north, sponge does it as (x, y,
			 * z-1)
			 * 
			 * when we thought we figured it out to be (x, y, z+1)
			 */
			if (turtleDirection == TurtleDirection.NORTH) {
				curLoc = curLoc.add(0, 0, 1);
				world.setBlock(x, y, z + i, bt);

			} else if (turtleDirection == TurtleDirection.NORTHEAST) {
				curLoc = curLoc.add(-1, 0, 1);
				world.setBlock(x - i, y, z + i, bt);

			} else if (turtleDirection == TurtleDirection.EAST) {
				curLoc = curLoc.add(-1, 0, 0);
				world.setBlock(x - i, y, z, bt);

			} else if (turtleDirection == TurtleDirection.SOUTHEAST) {
				curLoc = curLoc.add(-1, 0, -1);
				world.setBlock(x - i, y, z - i, bt);

			} else if (turtleDirection == TurtleDirection.SOUTH) {
				curLoc = curLoc.add(0, 0, -1);
				world.setBlock(x, y, z - i, bt);

			} else if (turtleDirection == TurtleDirection.SOUTHWEST) {
				curLoc = curLoc.add(1, 0, -1);
				world.setBlock(x + i, y, z - i, bt);

			} else if (turtleDirection == TurtleDirection.WEST) {
				curLoc = curLoc.add(1, 0, 0);
				world.setBlock(x + i, y, z, bt);

			} else if (turtleDirection == TurtleDirection.NORTHWEST) {
				curLoc = curLoc.add(1, 0, 1);
				world.setBlock(x + i, y, z + i, bt);

			} else if (turtleDirection == TurtleDirection.UP) {
				log.info("UP WAS CALLED");
				curLoc = curLoc.add(0, 1, 0);
				world.setBlock(x, y + i, z, bt);

			} else if (turtleDirection == TurtleDirection.DOWN) {
				curLoc = curLoc.add(0, -1, 0);
				world.setBlock(x, y - i, z, bt);

			} else {
				throw new RuntimeException("TurtleDirection invalid=" + turtleDirection);
			}
		}
	}

	public void executeScript(KCTScript script) {

		for (KCTCommand c : script.getCommands()) {
			try {
				executeCommand(c);
			} catch (TurtleCommandException e) {
				log.info("Unable to execute Turtle script:" + script.getScriptName());
				return;
			}
		}
	}

	private void executeCommand(KCTCommand c) throws TurtleCommandException {

		// get command info
		Map<String, Object> m = c.getArguments();
		String commandName = c.getCommandName();
		// execute command
		if (commandName.equals(KCTCommand.FORWARD)) {
			log.info("is m null? " + (m == null));
			log.info("m argument =" + m);
			// go forward
			int distance;
			if (!m.containsKey(KCTCommand.DIST)) {
				distance = 1; // default
			} else {
				distance = toInt(m.get(KCTCommand.DIST));
			}
			move(distance, dir);

		} else if (commandName.equals(KCTCommand.BACKWARD)) {
			// go backward
			int distance;
			if (!m.containsKey(KCTCommand.DIST)) {
				distance = 1;
			} else {
				distance = toInt(m.get(KCTCommand.DIST));
			}
			move(distance, dir.flip());

		} else if (commandName.equals(KCTCommand.TURNRIGHT)) {
			int degrees;
			if (!m.containsKey(KCTCommand.DEGREES)) {
				degrees = 90;
			} else {
				degrees = toInt(m.get(KCTCommand.DEGREES));
			}
			turn(false, degrees);

		} else if (commandName.equals(KCTCommand.TURNLEFT)) {
			int degrees;
			if (!m.containsKey(KCTCommand.DEGREES)) {
				degrees = 90;
			} else {
				degrees = toInt(m.get(KCTCommand.DEGREES));
			}
			turn(true, degrees);

		} else if (commandName.equals(KCTCommand.RIGHT)) {
			// strafe right
			int distance;
			if (!m.containsKey(KCTCommand.DIST)) {
				distance = 1;
			} else {
				distance = toInt(m.get(KCTCommand.DIST));
			}
			move(distance, dir.turn(false, 2));
		} else if (commandName.equals(KCTCommand.LEFT)) {
			// strafe left
			int distance;
			if (!m.containsKey(KCTCommand.DIST)) {
				distance = 1;
			} else {
				distance = toInt(m.get(KCTCommand.DIST));
			}
			move(distance, dir.turn(true, 2));
		} else if (commandName.equals(KCTCommand.UP)) {
			// go up
			int distance;
			if (!m.containsKey(KCTCommand.DIST)) {
				distance = 1;
			} else {
				distance = toInt(m.get(KCTCommand.DIST));
			}
			move(distance, TurtleDirection.UP);

		} else if (commandName.equals(KCTCommand.DOWN)) {
			// go down
			int distance;
			if (!m.containsKey(KCTCommand.DIST)) {
				distance = 1;
			} else {
				distance = toInt(m.get(KCTCommand.DIST));
			}
			move(distance, TurtleDirection.DOWN);
		} else if (commandName.equals(KCTCommand.SETBLOCK)) {
			String blockName = m.get(KCTCommand.BLOCKTYPE).toString();
			bt = KCTBlockTypesBuilder.getBlockState(KCTBlockTypes.valueOf(blockName));
		}
	}
}

// add execute command for other commands!
// PASS SCRIPT TO TURTLE AND EXECUTE IT