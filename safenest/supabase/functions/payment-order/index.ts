import { handleOrder } from './handler.mjs';
import {paymentEnv} from '../_shared/payment-env.ts';
Deno.serve((req: Request) => handleOrder(req,paymentEnv()));
