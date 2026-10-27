import {handleRtdn} from './handler.mjs';
import {playEnv} from '../_shared/play-env.ts';
Deno.serve((req: Request) => handleRtdn(req, playEnv()));
