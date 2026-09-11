{-
 Copyright (C) 2013-2017 Michal Antkiewicz <http://gsd.uwaterloo.ca>

 Permission is hereby granted, free of charge, to any person obtaining a copy of
 this software and associated documentation files (the "Software"), to deal in
 the Software without restriction, including without limitation the rights to
 use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies
 of the Software, and to permit persons to whom the Software is furnished to do
 so, subject to the following conditions:

 The above copyright notice and this permission notice shall be included in all
 copies or substantial portions of the Software.

 THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 SOFTWARE.
-}
{-# LANGUAGE OverloadedStrings #-}

module Language.Clafer.IG.JSONGenerator (generateJSON) where

import Language.Clafer.Common
import Language.Clafer.Intermediate.Intclafer
import qualified Language.Clafer.IG.ClaferModel as M
import qualified Data.Aeson.Encoding as AE
import Data.Maybe (fromJust)
import Data.String.Conversions
import Prelude hiding (id)

-- | Generate a representation of the instance in JSON format
generateJSON :: UIDIClaferMap -> M.ClaferModel                  -> String
generateJSON    uidIClaferMap'   (M.ClaferModel topLevelClafers) =
    convertString $ AE.encodingToLazyByteString $ AE.pairs $ constructElements $ map (printClafer uidIClaferMap') topLevelClafers

printClafer :: UIDIClaferMap -> M.Clafer                           -> AE.Series
printClafer    uidIClaferMap'      (M.Clafer id value children) =
    map (printClafer uidIClaferMap') children `addElements` completeClaferObject
    where
        uid' = M.i_name id
        iclafer = fromJust $ findIClafer uidIClaferMap' $ removeOrdinal uid'
        ident' = _ident iclafer

        super' = getSuper iclafer
        reference' = getReference iclafer
        (Just (cardMin, _)) = _card iclafer
        (Just (_, cardMax)) = _card iclafer
        basicClaferObject = makeBasicClaferObject ident' uid' super' reference' cardMin cardMax

        addValue :: Maybe M.Value         -> AE.Series -> AE.Series
        addValue    Nothing                  object = object
        addValue    (Just (M.IntValue i))    object = addIntValue i object
        addValue    (Just (M.AliasValue a))  object = addStringValue (M.i_name a) object
        addValue    (Just (M.StringValue _)) _      = error "Function addValue from JSONGenerator does not accept StringValues" -- Should never happen, string values are not generated yet

        completeClaferObject = addValue value basicClaferObject

        removeOrdinal :: String -> String
        removeOrdinal = takeWhile (/= '$')

makeBasicClaferObject :: String -> String -> [String] -> [String] -> Integer -> Integer -> AE.Series
makeBasicClaferObject    ident'    uid'      super'      reference'   cardMin    cardMax  =
    mconcat [ AE.pair "ident" $ AE.string ident',
              AE.pair "uid" $ AE.string uid',
              superRow,
              refRow,
              AE.pair "cardMin" $ AE.integer cardMin,
              AE.pair "cardMax" $ AE.integer cardMax ]
    where
        superRow = case super' of
            [s] -> AE.pair "super" $ AE.string s
            _   -> mempty
        refRow = case reference' of
            [r] -> AE.pair "reference" $ AE.string r
            _   -> mempty

addIntValue :: Int -> AE.Series      -> AE.Series
addIntValue    value  claferObject =
    claferObject `mappend` AE.pair "value" (AE.int value)

addStringValue :: String -> AE.Series      -> AE.Series
addStringValue    value     claferObject =
    claferObject `mappend` AE.pair "value" (AE.string value)

addElements :: [ AE.Series ] -> AE.Series      -> AE.Series
addElements    elements'      claferObject =
    claferObject `mappend` constructElements elements'

constructElements :: [ AE.Series ] -> AE.Series
constructElements    elements'    =
    AE.pair "elements" $ AE.list AE.pairs elements'
