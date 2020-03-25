package org.kevm.web3

import com.fasterxml.jackson.databind.ObjectMapper
import io.reactivex.Flowable
import org.kevm.rpc.jackson.RequestObjectMapper
import org.kevm.rpc.module.*
import org.kevm.rpc.KevmRpcService
import org.web3j.protocol.ObjectMapperFactory
import org.web3j.protocol.Web3jService
import org.web3j.protocol.core.Request
import org.web3j.protocol.core.Response
import org.web3j.protocol.websocket.events.Notification
import java.lang.RuntimeException
import java.util.concurrent.CompletableFuture
import org.web3j.protocol.core.Response.Error as Web3jError

class AdapterKevmWeb3jService(
    val service: KevmRpcService,
    val web3jObjectMapper: ObjectMapper = ObjectMapperFactory.getObjectMapper(false),
    val kevmObjectMapper: ObjectMapper = RequestObjectMapper().create(
        WebModule.supported() + NetModule.supported() + EthModule.supported()
    )
) : Web3jService {

    override fun <T : Response<*>?> send(request: Request<*, out Response<*>>, responseType: Class<T>): T {
        return try {
            val kevmRequest = web3RequestToKevm(request)
            val kevmResponse = service.processRequest(kevmRequest)
            kevmResponseToWeb3j(kevmResponse, responseType)
        } catch (e: Exception) {
            createErrorResponse(responseType, e, request)
        }
    }

    private fun <T : Response<*>?> kevmResponseToWeb3j(response: RpcResponse<*>?, responseType: Class<T>): T {
        val responseJson = kevmObjectMapper.writeValueAsString(response)
        return web3jObjectMapper.readValue(responseJson, responseType)
    }

    private fun web3RequestToKevm(request: Request<*, out Response<*>>): RpcRequest<*> {
        val requestJson = web3jObjectMapper.writeValueAsString(request)
        return kevmObjectMapper.readValue(requestJson, RpcRequest::class.java)
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T : Response<*>?> createErrorResponse(
        responseType: Class<T>,
        e: Exception,
        request: Request<*, out Response<*>>
    ): T {
        val instance: T = (responseType.constructors[0].newInstance() as T) ?: throw RuntimeException("can't create ${responseType} instance")
        instance?.setError(Web3jError(-1, e.message))
        instance?.setId(request.id)
        instance?.setJsonrpc(request.jsonrpc)

        return instance
    }

    override fun <T : Response<*>> sendAsync(
        request: Request<*, out Response<*>>,
        responseType: Class<T>
    ): CompletableFuture<T> = CompletableFuture.supplyAsync { send(request, responseType) }

    override fun <T : Notification<*>?> subscribe(
        request: Request<*, out Response<*>>?,
        unsubscribeMethod: String?,
        responseType: Class<T>?
    ): Flowable<T> {
        TODO("not implemented")
    }

    override fun close() {
    }
}