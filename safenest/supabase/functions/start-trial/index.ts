import {handleTrial} from './handler.mjs';
import {serviceRpc} from '../_shared/payments.mjs';
import {paymentEnv} from '../_shared/payment-env.ts';
const env=paymentEnv();
Deno.serve((req: Request)=>handleTrial(req,env,{rpc:serviceRpc(env)}));
