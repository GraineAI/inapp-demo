/**
 * The use-case packs, bundled from ../../usecases (the same files every platform reads; Metro
 * watches that folder — see metro.config.js). Add a pack here after adding its file.
 */
import health from "../../usecases/health-coach.json";
import banking from "../../usecases/banking-support.json";
import retail from "../../usecases/retail-shopping.json";
import type { UseCasePack } from "./logic/pack";

export const PACKS: ReadonlyArray<UseCasePack> = [health, banking, retail] as ReadonlyArray<UseCasePack>;

export const DEFAULT_PACK_ID = "health-coach";
